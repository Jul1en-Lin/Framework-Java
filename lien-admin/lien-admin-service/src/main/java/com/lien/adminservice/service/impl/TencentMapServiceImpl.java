package com.lien.adminservice.service.impl;

import com.lien.adminservice.domain.dto.GeoResultDTO;
import com.lien.adminservice.domain.dto.LocationDTO;
import com.lien.adminservice.domain.dto.PoiListDTO;
import com.lien.adminservice.domain.dto.SearchParamDTO;
import com.lien.adminservice.service.ITencentMapService;
import com.lien.api.constants.MapConstants;
import domain.EnumCode;
import domain.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RefreshScope
@ConditionalOnProperty(value = "map.type", havingValue = "tencentMap")
public class TencentMapServiceImpl implements ITencentMapService {

    /**
     * 腾讯位置服务的秘钥
     */
    @Value("${tencentMap.key}")
    private String key;

    /**
     * 腾讯位置服务域名
     */
    @Value("${tencentMap.apiServer}")
    private String apiServer;

    @Autowired
    private  RestTemplate restTemplate;

    @Override
    public PoiListDTO searchPlaceByRegion(SearchParamDTO searchParamDTO) {
        // 拼接 url
        String requestUrl = String.format(
                apiServer + MapConstants.Tencent_MAP_API_PLACE_SUGGESTION +
                        "?region=%s&keyword=%s&key=%s&region_fix=1&page_index=%s&page_size=%s",
                searchParamDTO.getRegionId(),searchParamDTO.getKeyword(),key,searchParamDTO.getPageIndex(), searchParamDTO.getPageSize()
        );

        // 发送请求并转换对象
        ResponseEntity<PoiListDTO> response = restTemplate.getForEntity(requestUrl, PoiListDTO.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("获取关键词查询结果异常", response);
            throw new ServiceException(EnumCode.Tencent_MAP_QUERY_FAILED);
        }
        return response.getBody();
    }

    @Override
    public GeoResultDTO getDistrictByLonLat(LocationDTO locationDTO) {
        // 1 构建请求url
        String url = String.format(apiServer + MapConstants.Tencent_MAP_GEOCODER +
                "?location=%s&key=%s",
                locationDTO.formatInfo(),key
        );
        // 2 直接发送请求，并拿到返回结果再做对象转换
        ResponseEntity<GeoResultDTO> response =  restTemplate.getForEntity(url, GeoResultDTO.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("根据经纬度来获取区域信息查询结果异常", response);
            throw new ServiceException(EnumCode.Tencent_MAP_QUERY_FAILED);
        }
        return response.getBody();
    }
}
