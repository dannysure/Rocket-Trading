package com.rockettrading.rocket_trading.controller;

import com.rockettrading.rocket_trading.dto.common.ApiResponse;
import com.rockettrading.rocket_trading.dto.reporting.ClientSegmentActivityResponse;
import com.rockettrading.rocket_trading.dto.reporting.InstrumentActivityResponse;
import com.rockettrading.rocket_trading.dto.reporting.ReportingOverviewResponse;
import com.rockettrading.rocket_trading.service.ReportingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reporting")
public class ReportingController {
    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<ReportingOverviewResponse>> getOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ResponseEntity.ok(ApiResponse.success(reportingService.getOverview(from, to)));
    }

    @GetMapping("/instruments")
    public ResponseEntity<ApiResponse<List<InstrumentActivityResponse>>> listInstrumentActivity(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ResponseEntity.ok(ApiResponse.success(reportingService.listInstrumentActivity(from, to)));
    }

    @GetMapping("/segments")
    public ResponseEntity<ApiResponse<List<ClientSegmentActivityResponse>>> listClientSegmentActivity(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ResponseEntity.ok(ApiResponse.success(reportingService.listClientSegmentActivity(from, to)));
    }
}
