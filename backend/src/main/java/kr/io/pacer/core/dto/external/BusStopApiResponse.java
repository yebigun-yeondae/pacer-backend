package kr.io.pacer.core.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.util.StdConverter;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BusStopApiResponse {

    private Response response;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {
        private Body body;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {
        // 데이터 없을 때 API가 items를 빈 문자열("")로 반환하는 경우 null로 처리
        @JsonSetter(nulls = Nulls.AS_EMPTY)
        @JsonDeserialize(converter = ItemsSafeConverter.class)
        private Items items;
        private int totalCount;
        private int numOfRows;
        private int pageNo;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Items {
        // 단건일 때 API가 배열 대신 단일 객체로 반환하는 경우 처리
        @JsonSetter(nulls = Nulls.AS_EMPTY)
        @com.fasterxml.jackson.annotation.JsonFormat(
                with = com.fasterxml.jackson.annotation.JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<Item> item;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String nodeid;
        private String nodenm;
        private String nodeno;
        private Integer citycode;
        private Double gpslati;
        private Double gpslong;
    }

    // items 필드가 객체가 아닌 다른 타입(빈 문자열 등)으로 오면 null 반환
    public static class ItemsSafeConverter extends StdConverter<Object, Items> {
        @Override
        public Items convert(Object value) {
            if (value instanceof Items items) return items;
            return null;
        }
    }
}
