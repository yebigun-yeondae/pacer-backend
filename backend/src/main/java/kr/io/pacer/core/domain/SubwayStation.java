package kr.io.pacer.core.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "subway_stations")
@Getter
@NoArgsConstructor
public class SubwayStation {

    @Id
    @Column(name = "station_cd")
    private String stationCd;

    @Column(name = "station_nm")
    private String stationNm;

    @Column(name = "line_num")
    private String lineNum;

    @Column(name = "station_nm_eng")
    private String stationNmEng;

    @Column(name = "fr_code")
    private String frCode;

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point geom;

    public static SubwayStation of(String stationCd, String stationNm, String lineNum,
                                   String stationNmEng, String frCode, Point geom) {
        SubwayStation s = new SubwayStation();
        s.stationCd    = stationCd;
        s.stationNm    = stationNm;
        s.lineNum      = lineNum;
        s.stationNmEng = stationNmEng;
        s.frCode       = frCode;
        s.geom         = geom;
        return s;
    }
}
