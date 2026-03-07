package com.eci.arep.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WebApplication {

    private static final int DEFAULT_PORT = 35000;
    private static final String DEFAULT_SCAN_PACKAGE = "com.eci.arep.web";

    private static final Map<String, RouteHandler> routes = new HashMap<>();

    public static void main(String[] args) throws Exception {
        AppConfig config = parseArgs(args);
        bootstrapControllers(config);
        startHttpServer(config.port);
    }

    private static AppConfig parseArgs(String[] args) {
        int port = DEFAULT_PORT;
        String scanPackage = DEFAULT_SCAN_PACKAGE;
        List<String> explicitControllers = new ArrayList<>();

        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                port = Integer.parseInt(arg.substring("--port=".length()));
            } else if (arg.startsWith("--scan=")) {
                scanPackage = arg.substring("--scan=".length());
            } else {
                explicitControllers.add(arg);
            }
        }

        return new AppConfig(port, scanPackage, explicitControllers);
    }

    private static void bootstrapControllers(AppConfig config) throws Exception {
        Set<Class<?>> controllerClasses = new HashSet<>();

        if (!config.explicitControllers.isEmpty()) {
            for (String fqcn : config.explicitControllers) {
                controllerClasses.add(Class.forName(fqcn));
            }
        } else {
            controllerClasses.addAll(scanForRestControllers(config.scanPackage));
        }

        for (Class<?> controllerClass : controllerClasses) {
            registerController(controllerClass);
        }

        System.out.println("Controllers loaded: " + controllerClasses.size());
        System.out.println("Routes loaded: " + routes.keySet());
    }

    private static Set<Class<?>> scanForRestControllers(String packageName) throws Exception {
        Set<Class<?>> classes = new HashSet<>();
        String packagePath = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = classLoader.getResources(packagePath);

        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            if (!"file".equals(resource.getProtocol())) {
                continue;
            }

            Path rootPath = Paths.get(resource.toURI());
            if (!Files.exists(rootPath)) {
                continue;
            }

            Files.walk(rootPath)
                    .filter(path -> path.toString().endsWith(".class"))
                    .forEach(path -> {
                        try {
                            String className = toClassName(path, rootPath, packageName);
                            Class<?> clazz = Class.forName(className);
                            if (clazz.isAnnotationPresent(RestController.class)) {
                                classes.add(clazz);
                            }
                        } catch (Exception ignored) {
                        }
                    });
        }

        return classes;
    }

    private static String toClassName(Path classFilePath, Path packageRoot, String packageName) {
        String relativePath = packageRoot.relativize(classFilePath).toString();
        String withoutExtension = relativePath.substring(0, relativePath.length() - ".class".length());
        String normalized = withoutExtension.replace('\\', '.').replace('/', '.');
        return packageName + "." + normalized;
    }

    private static void registerController(Class<?> controllerClass) throws Exception {
        if (!controllerClass.isAnnotationPresent(RestController.class)) {
            return;
        }

        Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();
        for (Method method : controllerClass.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(GetMapping.class)) {
                continue;
            }

            if (method.getReturnType() != String.class) {
                throw new IllegalArgumentException("Only String return type is supported for route: " + method.getName());
            }

            String path = method.getAnnotation(GetMapping.class).value();
            method.setAccessible(true);
            routes.put(path, new RouteHandler(controllerInstance, method));
        }
    }

    private static void startHttpServer(int port) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Micro server listening at http://localhost:" + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                handleClient(clientSocket);
            }
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (Socket socket = clientSocket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             OutputStream outputStream = socket.getOutputStream()) {

            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isBlank()) {
                return;
            }

            while (true) {
                String header = reader.readLine();
                if (header == null || header.isBlank()) {
                    break;
                }
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                writeResponse(outputStream, "400 Bad Request", "text/plain", "Invalid request".getBytes(StandardCharsets.UTF_8));
                return;
            }

            if (!"GET".equals(parts[0])) {
                writeResponse(outputStream, "405 Method Not Allowed", "text/plain", "Only GET is supported".getBytes(StandardCharsets.UTF_8));
                return;
            }

            String fullPath = parts[1];
            String path = fullPath;
            String query = "";

            int querySeparator = fullPath.indexOf('?');
            if (querySeparator >= 0) {
                path = fullPath.substring(0, querySeparator);
                query = fullPath.substring(querySeparator + 1);
            }

            RouteHandler handler = routes.get(path);
            if (handler != null) {
                String response = invokeRoute(handler, parseQueryParams(query));
                writeResponse(outputStream, "200 OK", "text/plain; charset=UTF-8", response.getBytes(StandardCharsets.UTF_8));
                return;
            }

            byte[] staticFile = loadStaticFile(path);
            if (staticFile != null) {
                writeResponse(outputStream, "200 OK", contentType(path), staticFile);
                return;
            }

            writeResponse(outputStream, "404 Not Found", "text/plain", "Resource not found".getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String invokeRoute(RouteHandler handler, Map<String, String> queryParams) throws Exception {
        Method method = handler.method();
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            RequestParam requestParam = parameter.getAnnotation(RequestParam.class);

            if (requestParam == null || parameter.getType() != String.class) {
                throw new IllegalArgumentException("Only String parameters with @RequestParam are supported in method: " + method.getName());
            }

            String value = queryParams.get(requestParam.value());
            if (value == null || value.isBlank()) {
                value = requestParam.defaultValue();
            }
            args[i] = value;
        }

        Object result = method.invoke(handler.controller(), args);
        return result == null ? "" : result.toString();
    }

    private static Map<String, String> parseQueryParams(String queryString) {
        Map<String, String> queryParams = new HashMap<>();
        if (queryString == null || queryString.isBlank()) {
            return queryParams;
        }

        Arrays.stream(queryString.split("&"))
                .map(part -> part.split("=", 2))
                .forEach(pair -> {
                    String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                    String value = pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
                    queryParams.put(key, value);
                });
        return queryParams;
    }

    private static byte[] loadStaticFile(String path) throws IOException {
        String resourcePath = normalizeStaticPath(path);
        if (resourcePath == null) {
            return null;
        }

        try (InputStream is = WebApplication.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                return null;
            }
            return is.readAllBytes();
        }
    }

    private static String normalizeStaticPath(String path) {
        String resolvedPath = "/".equals(path) ? "/index.html" : path;
        if (resolvedPath.contains("..")) {
            return null;
        }
        return "static" + resolvedPath;
    }

    private static String contentType(String path) {
        String lowerPath = path.toLowerCase();
        if (lowerPath.endsWith(".html") || "/".equals(lowerPath)) {
            return "text/html; charset=UTF-8";
        }
        if (lowerPath.endsWith(".png")) {
            return "image/png";
        }
        if (lowerPath.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        }
        if (lowerPath.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        }
        return "application/octet-stream";
    }

    private static void writeResponse(OutputStream outputStream, String status, String contentType, byte[] body) throws IOException {
        String headers = "HTTP/1.1 " + status + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";

        outputStream.write(headers.getBytes(StandardCharsets.UTF_8));
        outputStream.write(body);
        outputStream.flush();
    }

    private record RouteHandler(Object controller, Method method) {
    }

    private record AppConfig(int port, String scanPackage, List<String> explicitControllers) {
    }
}
