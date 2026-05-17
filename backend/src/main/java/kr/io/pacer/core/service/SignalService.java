package kr.io.pacer.core.service;

import kr.io.pacer.core.client.CitsSpatClient;
import kr.io.pacer.core.client.CitsSpatStateClient;
import kr.io.pacer.core.dto.external.SpatResponse;
import kr.io.pacer.core.dto.external.SpatStateResponse;
import kr.io.pacer.core.dto.response.SignalResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignalService {

    private final CitsSpatClient citsSpatClient;
    private final CitsSpatStateClient citsSpatStateClient;
    private final JdbcTemplate jdbcTemplate;

    public List<SignalResponse> getSignals(List<Integer> itstIds) {
        log.info("[Signal] 신호 조회 | count={} itstIds={}", itstIds.size(), itstIds);
        Map<Integer, String> nameMap = fetchNames(itstIds);
        Map<Integer, SpatResponse> spatMap = citsSpatClient.fetchAll(itstIds);
        Map<Integer, SpatStateResponse> stateMap = citsSpatStateClient.fetchAll(itstIds);

        return itstIds.stream()
                .map(id -> {
                    SpatResponse spat = spatMap.get(id);
                    SpatStateResponse state = stateMap.get(id);
                    return (SignalResponse) SignalResponse.builder()
                            .itstId(id)
                            .name(nameMap.getOrDefault(id, ""))
                            .ntPdsgRmdrCs(spat != null ? spat.getNtPdsgRmdrCs() : null)
                            .etPdsgRmdrCs(spat != null ? spat.getEtPdsgRmdrCs() : null)
                            .stPdsgRmdrCs(spat != null ? spat.getStPdsgRmdrCs() : null)
                            .wtPdsgRmdrCs(spat != null ? spat.getWtPdsgRmdrCs() : null)
                            .nePdsgRmdrCs(spat != null ? spat.getNePdsgRmdrCs() : null)
                            .sePdsgRmdrCs(spat != null ? spat.getSePdsgRmdrCs() : null)
                            .swPdsgRmdrCs(spat != null ? spat.getSwPdsgRmdrCs() : null)
                            .nwPdsgRmdrCs(spat != null ? spat.getNwPdsgRmdrCs() : null)
                            .ntPdsgStatNm(state != null ? state.getNtPdsgStatNm() : null)
                            .etPdsgStatNm(state != null ? state.getEtPdsgStatNm() : null)
                            .stPdsgStatNm(state != null ? state.getStPdsgStatNm() : null)
                            .wtPdsgStatNm(state != null ? state.getWtPdsgStatNm() : null)
                            .nePdsgStatNm(state != null ? state.getNePdsgStatNm() : null)
                            .sePdsgStatNm(state != null ? state.getSePdsgStatNm() : null)
                            .swPdsgStatNm(state != null ? state.getSwPdsgStatNm() : null)
                            .nwPdsgStatNm(state != null ? state.getNwPdsgStatNm() : null)
                            .build();
                })
                .toList();
    }

    private Map<Integer, String> fetchNames(List<Integer> itstIds) {
        String sql = "SELECT itst_id, name FROM intersections WHERE itst_id = ANY(?)";
        return jdbcTemplate.query(
                sql,
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("integer", itstIds.toArray())),
                (rs, rowNum) -> Map.entry(rs.getInt("itst_id"), rs.getString("name"))
        ).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
