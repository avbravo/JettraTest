package io.jettra.test.runner;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import io.jettra.test.annotation.JettraTest;

public class JettraTestRunner {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: JettraTestRunner <test-classes-dir> <classes-dir>");
            System.exit(1);
        }

        String testClassesDir = args[0];
        String targetDir = new File(testClassesDir).getParent(); // Usually "target"
        
        System.out.println("-------------------------------------------------------");
        System.out.println(" T E S T S  (JettraTest Framework)");
        System.out.println("-------------------------------------------------------");

        try {
            List<URL> urls = new ArrayList<>();
            for (String arg : args) {
                urls.add(new File(arg).toURI().toURL());
            }

            URLClassLoader classLoader = URLClassLoader.newInstance(urls.toArray(new URL[0]), JettraTestRunner.class.getClassLoader());

            List<Class<?>> classes = findClasses(new File(testClassesDir), testClassesDir, classLoader);
            
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
                            method.invoke(instance);
                        } catch (Throwable t) {
                            classFailures++;
                            totalFailures++;
                            failureDetails.append(method.getName()).append(" failed: ");
                            if (t.getCause() != null) {
                                failureDetails.append(t.getCause().toString()).append("\n");
                            } else {
                                failureDetails.append(t.toString()).append("\n");
                            }
                        }
                    }
                }

                if (classTests > 0) {
                    long endTime = System.currentTimeMillis();
                    double timeSec = (endTime - startTime) / 1000.0;
                    System.out.printf("Running %s%n", clazz.getName());
                    System.out.printf("Tests run: %d, Failures: %d, Errors: 0, Skipped: 0, Time elapsed: %.3f s%n", 
                                      classTests, classFailures, timeSec);
                    
                    SurefireReporter.writeReport(targetDir, clazz.getName(), classTests, classFailures, 0, 0, timeSec, failureDetails.toString());
                }
            }

            System.out.println("\nResults:\n");
            System.out.printf("Tests run: %d, Failures: %d, Errors: 0, Skipped: 0%n", totalTests, totalFailures);

            if (totalFailures > 0) {
                System.out.println("\n[ERROR] There are test failures.");
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
