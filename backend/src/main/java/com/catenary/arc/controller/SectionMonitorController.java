package com.catenary.arc.controller;

import com.catenary.arc.dto.ApiResponse;
import com.catenary.arc.service.SectionMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sections")
@RequiredArgsConstructor
public class SectionMonitorController {

    private final SectionMonitorService sectionMonitorService;

    @GetMapping("/all")
    public ApiResponse<Object> getAllSections() {
        return ApiResponse.success(sectionMonitorService.getAllSections());
    }

    @GetMapping("/bureau/{bureauId}")
    public ApiResponse<Object> getSectionsByBureau(@PathVariable String bureauId) {
        return ApiResponse.success(sectionMonitorService.getSectionsByBureau(bureauId));
    }

    @GetMapping("/station/{stationId}")
    public ApiResponse<Object> getSectionsByStation(@PathVariable String stationId) {
        return ApiResponse.success(sectionMonitorService.getSectionsByStation(stationId));
    }

    @GetMapping("/work-area/{workAreaId}")
    public ApiResponse<Object> getSectionsByWorkArea(@PathVariable String workAreaId) {
        return ApiResponse.success(sectionMonitorService.getSectionsByWorkArea(workAreaId));
    }

    @GetMapping("/{sectionId}")
    public ApiResponse<Object> getSectionDetail(@PathVariable String sectionId) {
        return ApiResponse.success(sectionMonitorService.getSectionDetail(sectionId));
    }
}
