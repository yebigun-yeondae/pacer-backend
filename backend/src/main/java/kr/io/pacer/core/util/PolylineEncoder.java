package kr.io.pacer.core.util;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PolylineEncoder {

    public String toWkt(String encodedPolyline) {
        List<double[]> points = decode(encodedPolyline);
        String coords = points.stream()
                .map(p -> p[1] + " " + p[0])  // WKT: lng lat
                .collect(Collectors.joining(", "));
        return "LINESTRING(" + coords + ")";
    }

    public String merge(List<String> encodedPolylines) {
        List<double[]> merged = new ArrayList<>();
        for (int i = 0; i < encodedPolylines.size(); i++) {
            List<double[]> points = decode(encodedPolylines.get(i));
            if (i > 0 && !merged.isEmpty()) {
                points = points.subList(1, points.size());
            }
            merged.addAll(points);
        }
        return encode(merged);
    }

    private String encode(List<double[]> points) {
        StringBuilder sb = new StringBuilder();
        int prevLat = 0, prevLng = 0;
        for (double[] p : points) {
            int lat = (int) Math.round(p[0] * 1e6);
            int lng = (int) Math.round(p[1] * 1e6);
            encodeValue(sb, lat - prevLat);
            encodeValue(sb, lng - prevLng);
            prevLat = lat;
            prevLng = lng;
        }
        return sb.toString();
    }

    private void encodeValue(StringBuilder sb, int value) {
        value = value < 0 ? ~(value << 1) : (value << 1);
        while (value >= 0x20) {
            sb.append((char) ((0x20 | (value & 0x1f)) + 63));
            value >>= 5;
        }
        sb.append((char) (value + 63));
    }

    // Valhalla 기본 포맷인 polyline6 (정밀도 1e-6) 디코더
    public List<double[]> decode(String encoded) {
        List<double[]> points = new ArrayList<>();
        int index = 0, lat = 0, lng = 0;
        while (index < encoded.length()) {
            int result = 0, shift = 0, b;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            lat += ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);

            result = 0; shift = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            lng += ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);

            points.add(new double[]{lat / 1e6, lng / 1e6});
        }
        return points;
    }
}
