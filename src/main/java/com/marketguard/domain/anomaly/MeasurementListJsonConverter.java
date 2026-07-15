package com.marketguard.domain.anomaly;

import com.marketguard.detection.model.EvidenceMeasurement;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Converter
public class MeasurementListJsonConverter
        implements AttributeConverter<List<EvidenceMeasurement>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<EvidenceMeasurement>> TYPE = new TypeReference<>() { };

    @Override
    public String convertToDatabaseColumn(List<EvidenceMeasurement> attribute) {
        try {
            return MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("failed to serialize evidence measurements", exception);
        }
    }

    @Override
    public List<EvidenceMeasurement> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return List.copyOf(MAPPER.readValue(value, TYPE));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("failed to deserialize evidence measurements", exception);
        }
    }
}
