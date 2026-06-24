package de.oliver.fancynpcs.runtime;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class ReflectionCalls {

    private ReflectionCalls() {
    }

    static Object invoke(Object target, String methodName, Object... arguments) throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), methodName, false, arguments);
        return method.invoke(target, arguments);
    }

    static Object invokeStatic(Class<?> type, String methodName, Object... arguments) throws ReflectiveOperationException {
        Method method = findMethod(type, methodName, true, arguments);
        return method.invoke(null, arguments);
    }

    static Object invokeIfPresent(Object target, String methodName, Object... arguments) {
        if (target == null) {
            return null;
        }
        try {
            return invoke(target, methodName, arguments);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    static Class<?> loadFromPlugin(String pluginName, String className) throws ClassNotFoundException {
        Plugin dependency = Bukkit.getPluginManager().getPlugin(pluginName);
        if (dependency == null || !dependency.isEnabled()) {
            throw new ClassNotFoundException("Plugin " + pluginName + " is not enabled");
        }
        return Class.forName(className, true, dependency.getClass().getClassLoader());
    }

    private static Method findMethod(Class<?> type, String methodName, boolean requireStatic, Object[] arguments) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(methodName) || Modifier.isStatic(method.getModifiers()) != requireStatic) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != arguments.length) {
                continue;
            }
            boolean matches = true;
            for (int i = 0; i < parameterTypes.length; i++) {
                if (!isCompatible(parameterTypes[i], arguments[i])) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + methodName);
    }

    private static boolean isCompatible(Class<?> parameterType, Object argument) {
        if (argument == null) {
            return !parameterType.isPrimitive();
        }
        if (parameterType.isInstance(argument)) {
            return true;
        }
        if (!parameterType.isPrimitive()) {
            return false;
        }
        return (parameterType == boolean.class && argument instanceof Boolean)
                || (parameterType == int.class && argument instanceof Integer)
                || (parameterType == long.class && argument instanceof Long)
                || (parameterType == float.class && argument instanceof Float)
                || (parameterType == double.class && argument instanceof Double)
                || isWideningPrimitiveConversion(parameterType, argument);
    }

    private static boolean isWideningPrimitiveConversion(Class<?> parameterType, Object argument) {
        if (parameterType == double.class) {
            return argument instanceof Number;
        }
        if (parameterType == float.class) {
            return argument instanceof Byte || argument instanceof Short
                    || argument instanceof Integer || argument instanceof Long;
        }
        if (parameterType == long.class) {
            return argument instanceof Byte || argument instanceof Short || argument instanceof Integer;
        }
        if (parameterType == int.class) {
            return argument instanceof Byte || argument instanceof Short;
        }
        return parameterType == short.class && argument instanceof Byte;
    }
}
