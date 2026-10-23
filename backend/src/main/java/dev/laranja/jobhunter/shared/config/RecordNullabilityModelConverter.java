package dev.laranja.jobhunter.shared.config;

import java.lang.reflect.RecordComponent;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;

/**
 * Documenta a nulidade dos records da API: todo componente é obrigatório, exceto os anotados com
 * {@link Nullable}, que ficam opcionais e aceitam {@code null}. O frontend gera os tipos TypeScript a partir
 * disso.
 */
@Component
class RecordNullabilityModelConverter implements ModelConverter {

	@Override
	public Schema<?> resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
		Schema<?> schema = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
		Class<?> rawClass = Json.mapper().constructType(type.getType()).getRawClass();
		if (schema == null || !rawClass.isRecord()) {
			return schema;
		}
		Schema<?> definition = definitionOf(schema, context);
		if (definition == null || definition.getProperties() == null) {
			return schema;
		}
		Map<String, Schema> properties = definition.getProperties();
		for (RecordComponent component : rawClass.getRecordComponents()) {
			Schema<?> property = properties.get(component.getName());
			if (property == null) {
				continue;
			}
			if (component.getAnnotatedType().isAnnotationPresent(Nullable.class)) {
				properties.put(component.getName(), nullable(property));
			}
			else if (definition.getRequired() == null || !definition.getRequired().contains(component.getName())) {
				definition.addRequiredItem(component.getName());
			}
		}
		return schema;
	}

	private static Schema<?> definitionOf(Schema<?> schema, ModelConverterContext context) {
		if (schema.get$ref() == null) {
			return schema;
		}
		String name = schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1);
		return context.getDefinedModels().get(name);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Schema<?> nullable(Schema<?> property) {
		if (property.get$ref() != null) {
			Schema reference = new Schema<>().$ref(property.get$ref());
			Schema nullType = new Schema<>();
			nullType.addType("null");
			return new Schema<>().anyOf(List.of(reference, nullType));
		}
		Set<String> types = (property.getTypes() != null) ? new LinkedHashSet<>(property.getTypes())
				: new LinkedHashSet<>();
		if (types.isEmpty() && property.getType() != null) {
			types.add(property.getType());
		}
		types.add("null");
		property.setTypes(types);
		return property;
	}

}
