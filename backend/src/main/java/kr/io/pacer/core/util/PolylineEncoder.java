package kr.io.pacer.core.util;

import com.google.maps.internal.PolylineEncoding;
import com.google.maps.model.LatLng;
import kr.io.pacer.core.dto.internal.RouteSegment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PolylineEncoder {

    public String encode(List<RouteSegment> segments) {
        List<LatLng> points = segments.stream()
                .map(s -> new LatLng(s.getEndLat(), s.getEndLng()))
                .toList();
        return PolylineEncoding.encode(points);
    }
}
