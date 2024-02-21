package org.example;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// Custom annotation to mark classes as components
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Component {}

// Custom annotation to mark methods as request handlers
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface RequestMapping {
    String value();
}

// Fake HTTP request class
class HttpRequest {
    String method;
    String path;

    public HttpRequest(String method, String path) {
        this.method = method;
        this.path = path;
    }
}

// Fake HTTP response class
class HttpResponse {
    private final int statusCode;
    private final Map<String, Object> body;

    public HttpResponse() {
        this.statusCode = 200; // default status code
        this.body = new HashMap<>();
    }

    public HttpResponse(int statusCode, Map<String, Object> body) {
        this.statusCode = statusCode;
        this.body = Collections.unmodifiableMap(body);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Map<String, Object> getBody() {
        return body;
    }
}

// Simple DI container
class Container {
    private Map<Class<?>, Object> instances = new HashMap<>();

    public void register(Class<?> clazz, Object instance) {
        instances.put(clazz, instance);
    }

    public Object getBean(Class<?> clazz) {
        return instances.get(clazz);
    }
    public Map<Class<?>, Object> getInstances() {
        return instances;
    }

    public void autowire(Object instance) {
        Field[] fields = instance.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(Autowired.class)) {
                Object dependency = instances.get(field.getType());
                if (dependency != null) {
                    field.setAccessible(true);
                    try {
                        field.set(instance, dependency);
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}

// Autowired annotation
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface Autowired {}

// Simple web framework
class LiteSpringBoot {
    private Container container = new Container();

    public LiteSpringBoot(Class<?>... classes) {
        // Register all components
        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(Component.class)) {
                try {
                    Object instance = clazz.getDeclaredConstructor().newInstance();
                    container.register(clazz, instance);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // Autowire dependencies after registering components
        for (Object instance : container.getInstances().values()) {
            container.autowire(instance);
        }
    }

    public HttpResponse handleRequest(HttpRequest request) {
        // Find the appropriate request handler and invoke it
        Object handler = container.getBean(requestHandlerFor(request));
        if (handler != null) {
            try {
                Method[] methods = handler.getClass().getMethods();
                for (Method method : methods) {
                    if (method.isAnnotationPresent(RequestMapping.class)) {
                        RequestMapping mapping = method.getAnnotation(RequestMapping.class);
                        if (mapping.value().equals(request.path)) {
                            return (HttpResponse) method.invoke(handler, request);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private Class<?> requestHandlerFor(HttpRequest request) {
        // Simple logic to find the request handler based on the request path
        return MyController.class; // Replace with your own logic
    }
}

// Example controller
@Component
class MyController {
    @Autowired
    private MyService myService;

    @RequestMapping("/hello")
    public HttpResponse handleRequest(HttpRequest request) {
        System.out.println("Handling request for path: " + request.path);
        myService.doSomething();

        Map<String, Object> responseBody = Map.of("message", "Hello, World!");
        return new HttpResponse(200, responseBody);
    }
}

@Component
class MyService {
    public void doSomething() {
        System.out.println("MyService doing something...");
    }
}

public class Main {
    public static void main(String[] args) {
        LiteSpringBoot app = new LiteSpringBoot(MyController.class, MyService.class);
        HttpRequest request = new HttpRequest("GET", "/hello");
        HttpResponse response = app.handleRequest(request);
        System.out.println("Response: " + response.getBody());
    }
}
