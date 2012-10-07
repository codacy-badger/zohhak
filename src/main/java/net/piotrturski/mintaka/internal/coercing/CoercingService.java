package net.piotrturski.mintaka.internal.coercing;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import net.piotrturski.mintaka.internal.model.SingleTestMethod;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.tapestry5.plastic.PlasticUtils;

/* start with string. add everything that starts from string. 
 * while true (not converted)
 * 		check if we have matching tuple (if we reached tartgetType). generics?!
 * 			if yes - try to use it without exception and result
 * 		add everything that starts from the end of existing tuples and remove all tuples from that iteration (shortest)
 * end of while
 * throw coercion failed
 */

public class CoercingService {

	public Cache cache;
	private static final Class<?>[] COERCION_PARAMETERS = new Class<?>[] { String.class };

	public Object[] coerceParameters(SingleTestMethod method) {
		List<Coercion> methodCoercions = coercionsForMethod(method);

		Type[] genericParameterTypes = method.realMethod.getGenericParameterTypes();
		String[] parametersToParse = method.splitedParameters;

		int numberOfParams = parametersToParse.length;
		Object[] parameters = new Object[numberOfParams];
		for (int i = 0; i < numberOfParams; i++) {
			parameters[i] = coerceParameter(genericParameterTypes[i], parametersToParse[i], methodCoercions);
		}
		return parameters;
	}

	private List<Coercion> coercionsForMethod(SingleTestMethod method) {
		List<Coercion> methodCoercions = cache.getCoercionsForTestMethod(method.realMethod);
		if (methodCoercions == null) {

			List<Class<?>> coercers = method.configuration.getCoercers();
			methodCoercions = findCoercions(coercers);
			cache.addCoercionsForTestMethod(method.realMethod, methodCoercions);

		}

		return methodCoercions;
	}

	List<Coercion> findCoercions(List<Class<?>> coercers) { //TODO cache all coercions for class
		List<Coercion> foundCoercions = new ArrayList<Coercion>();
		for (Class<?> clazz : coercers) {
			Method[] methods = clazz.getMethods();
			for (Method method : methods) {
				if (isValidCoercionMethod(method)) {
					foundCoercions.add(new Coercion(method));
				}
			}
		}
		return foundCoercions;
	}

	boolean isValidCoercionMethod(Method method) {
		Class<?>[] parameters = method.getParameterTypes();
		return ArrayUtils.isEquals(parameters, COERCION_PARAMETERS) && method.getReturnType() != Void.TYPE;
	}

	Object coerceParameter(Type type, String stringToParse, List<Coercion> methodCoercions) {
		try {
			if ("null".equalsIgnoreCase(stringToParse)) {
				return null;
			}
			if (type instanceof Class) {
				Class<?> targetType = PlasticUtils.toWrapperType((Class<?>) type);

				Coercion foundCoercion = null;
				for (Coercion coercion : methodCoercions) {
					if (targetType.isAssignableFrom(coercion.getTargetType())) {
						foundCoercion = coercion;
						break;
					}
				}

				if (foundCoercion != null) {
					return cache.invokeCoercion(foundCoercion, stringToParse);
				}

				if (targetType.isEnum()) {
					return instantiateEnum(stringToParse, targetType);
				}
			}
		} catch (Exception e) {
			throw new IllegalArgumentException(createCoercionExceptionMessage(stringToParse, type), e);
		}
		throw new IllegalArgumentException(createCoercionExceptionMessage(stringToParse, type));
	}

	// unable to instantiate Enum from Class<?> without warnings
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Object instantiateEnum(String stringToParse, Class<?> targetType) {
		return Enum.valueOf((Class) targetType, stringToParse);
	}

	private String createCoercionExceptionMessage(String stringToParse, Type targetType) {
		return String.format("cannot interpret string %s as a %s", stringToParse, targetType);
	}

}
