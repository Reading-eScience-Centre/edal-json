package uk.ac.rdg.resc.edal.json;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import uk.ac.rdg.resc.edal.feature.GridFeature;
import uk.ac.rdg.resc.edal.feature.ProfileFeature;

import com.google.common.collect.ImmutableMap;

public class FeatureTypes {

	private static final Map<Class<?>,String> typeToName = ImmutableMap.of(
		GridFeature.class, "Grid",
		ProfileFeature.class, "Profile"
		);
	
	private static final Map<String, Class<?>> nameToType = new HashMap<>();
	
	static {
		for (Entry<Class<?>,String> e : typeToName.entrySet()) {
			nameToType.put(e.getValue(), e.getKey());
		}
	}
	
	public static String getName(Class<?> type) {
		String name = typeToName.get(type);
		if (name == null) {
			throw new IllegalArgumentException(type.getName());
		}
		return name;
	}
	
	public static Class<?> getType(String name) {
		Class<?> type = nameToType.get(name);
		if (type == null) {
			throw new IllegalArgumentException(name);
		}
		return type;
	}

}
