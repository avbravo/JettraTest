package io.jettra.test.runner;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import io.jettra.test.annotation.JettraTest;
import io.jettra.report.exporter.SurefireReporter;

public class JettraTestRunner {

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_CYAN = "\u001B[36m";

    public static void main(String[] args) {
        if (Boolean.getBoolean("skipTests") || Boolean.getBoolean("maven.test.skip") || "true".equals(System.getProperty("skipTests")) || "true".equals(System.getProperty("maven.test.skip"))) {
            System.out.println(ANSI_YELLOW + "Tests are skipped." + ANSI_RESET);
            return;
        }
        
        if (args.length == 0) {
            System.err.println(ANSI_RED + "Usage: JettraTestRunner <test-classes-dir> <classes-dir>" + ANSI_RESET);
            System.exit(1);
        }

        String testClassesDir = args[0];
        String targetDir = new File(testClassesDir).getParent(); // Usually "target"
        
        System.out.println(ANSI_CYAN + "-------------------------------------------------------");
        System.out.println(" T E S T S  (JettraTest Framework)");
        System.out.println("-------------------------------------------------------" + ANSI_RESET);

        try {
            List<URL> urls = new ArrayList<>();
            for (String arg : args) {
                urls.add(new File(arg).toURI().toURL());
            }

            URLClassLoader classLoader = URLClassLoader.newInstance(urls.toArray(new URL[0]), JettraTestRunner.class.getClassLoader());

            List<Class<?>> classes = findClasses(new File(testClassesDir), testClassesDir, classLoader);
            
            // Phase 1: Analyze classes for requirements and launcher
            boolean requiresServer = false;
            Class<?> launcherClass = null;
            
            for (Class<?> clazz : classes) {
                if (clazz.isAnnotationPresent(io.jettra.test.annotation.JettraTestLauncher.class)) {
                    launcherClass = clazz;
                }
                
                // Determine if tests need server
                boolean hasTest = false;
                for (Method m : clazz.getDeclaredMethods()) {
                    if (m.isAnnotationPresent(JettraTest.class)) {
                        hasTest = true;
                        break;
                    }
                }
                if (hasTest && !clazz.isAnnotationPresent(io.jettra.test.annotation.NotRequiresRunningServer.class)) {
                    requiresServer = true;
                }
            }
            
            // Phase 2: Start Server if required
            int testPort = 0;
            Object launcherInstance = null;
            if (requiresServer) {
                try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
                    testPort = socket.getLocalPort();
                } catch (Exception e) {
                    testPort = 9002;
                }
                
                if (launcherClass != null) {
                    System.out.println(ANSI_CYAN + "[JettraTestRunner] Server required. Starting via " + launcherClass.getName() + " on port " + testPort + ANSI_RESET);
                    try {
                        launcherInstance = launcherClass.getDeclaredConstructor().newInstance();
                        Method startMethod = launcherClass.getMethod("startServer", int.class);
                        startMethod.invoke(launcherInstance, testPort);
                        Thread.sleep(1000); // Give server time to bind
                    } catch (Exception e) {
                        System.err.println(ANSI_RED + "[JettraTestRunner] Failed to start server using launcher: " + e.getMessage() + ANSI_RESET);
                        e.printStackTrace();
                    }
                } else {
                    System.err.println(ANSI_YELLOW + "[JettraTestRunner] [WARNING] Server is required by tests, but no class annotated with @JettraTestLauncher was found!" + ANSI_RESET);
                }
            }
            
            int totalTests = 0;
            int totalFailures = 0;

            for (Class<?> clazz : classes) {
                int classTests = 0;
                int classFailures = 0;
                StringBuilder failureDetails = new StringBuilder();
                long startTime = System.currentTimeMillis();

                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(JettraTest.class)) {
                        classTests++;
                        totalTests++;
                        try {
                            Object instance = clazz.getDeclaredConstructor().newInstance();
                            
                            // Inject dynamic port if server is running
                            if (requiresServer && testPort > 0) {
                                try {
                                    java.lang.reflect.Field field = clazz.getDeclaredField("ServerPortTest");
                                    field.setAccessible(true);
                                    if (field.getType() == Integer.class || field.getType() == int.class) {
                                        field.set(instance, testPort);
                                    } else if (field.getType() == String.class) {
                                        field.set(instance, String.valueOf(testPort));
                                    }
                                } catch (NoSuchFieldException e) {
                                    try {
                                        java.lang.reflect.Field field = clazz.getDeclaredField("serverPortTest");
                                        field.setAccessible(true);
                                        if (field.getType() == Integer.class || field.getType() == int.class) {
                                            field.set(instance, testPort);
                                        } else if (field.getType() == String.class) {
                                            field.set(instance, String.valueOf(testPort));
                                        }
                                    } catch (NoSuchFieldException ignored) {
                                    }
                                }
                            }
                            
                            method.invoke(instance);
                        } catch (Throwable t) {
                            classFailures++;
                            totalFailures++;
                            
                            String errorMessage = (t.getCause() != null) ? t.getCause().toString() : t.toString();
                            failureDetails.append(method.getName()).append(" failed: ").append(errorMessage).append("\n");
                            
                            // Print the error directly to the console to simulate standard Maven behavior
                            System.err.println(ANSI_RED + "  <<< FAILURE! -- in " + clazz.getName());
                            System.err.println("      Method: " + method.getName() + "()");
                            System.err.println("      Reason: " + errorMessage + ANSI_RESET);
                        }
                    }
                }

                if (classTests > 0) {
                    long endTime = System.currentTimeMillis();
                    double timeSec = (endTime - startTime) / 1000.0;
                    System.out.printf("Running %s%n", clazz.getName());
                    String resultColor = classFailures > 0 ? ANSI_RED : ANSI_GREEN;
                    System.out.printf(resultColor + "Tests run: %d, Failures: %d, Errors: 0, Skipped: 0, Time elapsed: %.3f s%n" + ANSI_RESET, 
                                      classTests, classFailures, timeSec);
                    
                    SurefireReporter.writeReport(targetDir, clazz.getName(), classTests, classFailures, 0, 0, timeSec, failureDetails.toString());
                }
            }

            System.out.println("\nResults:\n");
            String totalColor = totalFailures > 0 ? ANSI_RED : ANSI_GREEN;
            System.out.printf(totalColor + "Tests run: %d, Failures: %d, Errors: 0, Skipped: 0%n" + ANSI_RESET, totalTests, totalFailures);

            // Phase 3: Stop server
            if (launcherInstance != null) {
                System.out.println(ANSI_CYAN + "[JettraTestRunner] Stopping server via " + launcherClass.getName() + ANSI_RESET);
                try {
                    Method stopMethod = launcherClass.getMethod("stopServer");
                    stopMethod.invoke(launcherInstance);
                } catch (Exception e) {
                    System.err.println(ANSI_RED + "[JettraTestRunner] Failed to stop server: " + e.getMessage() + ANSI_RESET);
                }
            }

            if (totalFailures > 0) {
                System.out.println("\n" + ANSI_RED + "[ERROR] There are test failures." + ANSI_RESET);
                System.exit(1);
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static List<Class<?>> findClasses(File directory, String rootDir, ClassLoader classLoader) {
        List<Class<?>> classes = new ArrayList<>();
        if (!directory.exists()) {
            return classes;
        }
        
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    classes.addAll(findClasses(file, rootDir, classLoader));
                } else if (file.getName().endsWith(".class")) {
                    String className = file.getAbsolutePath().replace(rootDir, "").replace(File.separator, ".");
                    if (className.startsWith(".")) {
                        className = className.substring(1);
                    }
                    className = className.replace(".class", "");
                    try {
                        classes.add(classLoader.loadClass(className));
                    } catch (ClassNotFoundException e) {
                        // ignore
                    }
                }
            }
        }
        return classes;
    }
}
