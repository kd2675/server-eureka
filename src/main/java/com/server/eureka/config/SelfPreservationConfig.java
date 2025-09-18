package com.server.eureka.config;

import com.netflix.eureka.EurekaServerContext;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.PostConstruct;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class SelfPreservationConfig {
    
    private final EurekaServerContext eurekaServerContext;
    private final MeterRegistry meterRegistry;
    
    @PostConstruct
    public void initSelfPreservationMetrics() {
        try {
            // ✅ MeterRegistry에 직접 등록하는 방식으로 수정
            meterRegistry.gauge("eureka.server.renewal.threshold", this, 
                SelfPreservationConfig::getRenewalThreshold);
                
            meterRegistry.gauge("eureka.server.renewal.rate", this, 
                SelfPreservationConfig::getCurrentRenewalRate);
                
            meterRegistry.gauge("eureka.server.self.preservation.ratio", this, 
                SelfPreservationConfig::getSelfPreservationRatio);
                
            log.info("✅ Self-preservation metrics initialized successfully");
            
        } catch (Exception e) {
            log.error("❌ Failed to initialize self-preservation metrics", e);
        }
    }
    
    @Scheduled(fixedDelay = 60000) // 1분마다
    public void logSelfPreservationStatus() {
        try {
            boolean selfPreservation = eurekaServerContext.getRegistry().isSelfPreservationModeEnabled();
            int threshold = eurekaServerContext.getRegistry().getNumOfRenewsPerMinThreshold();
            
            long currentLong = eurekaServerContext.getRegistry().getNumOfRenewsInLastMin();
            int current = (int) Math.min(currentLong, Integer.MAX_VALUE);
            
            double ratio = threshold > 0 ? (double) current / threshold : 0;
            
            // 📊 포맷팅된 문자열 미리 생성
            String ratioStr = String.format("%.2f", ratio);
            String statusInfo = String.format("Ratio: %s (%d/%d)", ratioStr, current, threshold);
            
            if (selfPreservation) {
                log.warn("🛡️  Self-Preservation Mode ACTIVE - {}", statusInfo);
                log.warn("🚨 Registry protection is ON - Instances will NOT be expired");
            } else {
                log.debug("✅ Self-Preservation Mode INACTIVE - {}", statusInfo);
                
                // 📈 추가 상태 정보
                if (ratio < 0.85) {
                    log.warn("⚠️  Renewal ratio is low: {} - Close to self-preservation activation", ratioStr);
                    log.info("💡 Current renewals are {}% of expected threshold", 
                            String.format("%.1f", ratio * 100));
                }
            }
            
            // 📊 주기적 요약 (매 10분마다)
            if (System.currentTimeMillis() % 600000 < 60000) { // 10분마다 한번
                logDetailedStatus(selfPreservation, current, threshold, ratio);
            }
            
        } catch (Exception e) {
            log.error("❌ Error logging self-preservation status", e);
        }
    }

    /**
     * 📊 상세 상태 로깅 (10분마다)
     */
    private void logDetailedStatus(boolean selfPreservation, int current, int threshold, double ratio) {
        log.info("═══════════════ 🛡️  SELF-PRESERVATION STATUS ═══════════════");
        log.info("📊 Mode: {} | Ratio: {} | Renewals: {}/{}", 
                selfPreservation ? "🟥 ACTIVE" : "🟩 INACTIVE",
                String.format("%.3f", ratio),
                current, threshold);
        
        if (selfPreservation) {
            log.info("🔒 Protection: Instance expiration is DISABLED");
            log.info("💡 Reason: Network issues detected or insufficient renewals");
            log.info("🔧 Action: Monitor network connectivity and service health");
        } else {
            log.info("⚡ Operation: Normal instance management");
            if (ratio > 1.2) {
                log.info("💚 Health: Excellent - All services are renewing properly");
            } else if (ratio > 0.95) {
                log.info("💛 Health: Good - Most services are healthy");
            } else {
                log.info("🧡 Health: Caution - Some services may have issues");
            }
        }
        log.info("═══════════════════════════════════════════════════════════");
    }
    
    /**
     * ✅ Self-preservation 메트릭 계산 메서드들
     */
    public double getRenewalThreshold() {
        try {
            return (double) eurekaServerContext.getRegistry().getNumOfRenewsPerMinThreshold();
        } catch (Exception e) {
            log.warn("Cannot get renewal threshold", e);
            return 0.0;
        }
    }
    
    public double getCurrentRenewalRate() {
        try {
            long currentLong = eurekaServerContext.getRegistry().getNumOfRenewsInLastMin();
            double rate = (double) currentLong;
            
            // ✅ 디버그 로그도 올바른 포맷으로
            log.debug("Current renewal rate: {}", String.format("%.1f", rate));
            
            return rate;
        } catch (Exception e) {
            log.warn("Cannot get current renewal rate: {}", e.getMessage());
            return 0.0;
        }
    }
    
    public double getSelfPreservationRatio() {
        try {
            int threshold = eurekaServerContext.getRegistry().getNumOfRenewsPerMinThreshold();
            long currentLong = eurekaServerContext.getRegistry().getNumOfRenewsInLastMin();
            int current = (int) Math.min(currentLong, Integer.MAX_VALUE);
            
            double ratio = threshold > 0 ? (double) current / threshold : 0.0;
            
            // ✅ 올바른 포맷팅
            log.trace("Self-preservation ratio calculated: {} (current: {}, threshold: {})", 
                String.format("%.4f", ratio), current, threshold);
        
            return ratio;
        } catch (Exception e) {
            log.warn("Cannot calculate self-preservation ratio: {}", e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * ✅ Self-preservation 상태 정보 반환 (API용)
     */
    public SelfPreservationStatus getSelfPreservationStatus() {
        try {
            boolean enabled = eurekaServerContext.getRegistry().isSelfPreservationModeEnabled();
            int threshold = eurekaServerContext.getRegistry().getNumOfRenewsPerMinThreshold();
            long currentLong = eurekaServerContext.getRegistry().getNumOfRenewsInLastMin();
            int current = (int) Math.min(currentLong, Integer.MAX_VALUE);
            double ratio = threshold > 0 ? (double) current / threshold : 0.0;
            
            return SelfPreservationStatus.builder()
                .enabled(enabled)
                .threshold(threshold)
                .current(current)
                .ratio(ratio)
                .status(getPreservationStatusText(enabled, ratio))
                .build();
                
        } catch (Exception e) {
            log.error("Error getting self-preservation status", e);
            return SelfPreservationStatus.builder()
                .enabled(false)
                .threshold(0)
                .current(0)
                .ratio(0.0)
                .status("ERROR: " + e.getMessage())
                .build();
        }
    }
    
    private String getPreservationStatusText(boolean enabled, double ratio) {
        if (enabled) {
            return "🛡️ ACTIVE - Protection mode is ON";
        } else if (ratio < 0.85) {
            return "⚠️ WARNING - Close to activation threshold";
        } else {
            return "✅ HEALTHY - Normal operation";
        }
    }
    
    /**
     * ✅ Self-preservation 상태 정보 클래스
     */
    @lombok.Data
    @lombok.Builder
    public static class SelfPreservationStatus {
        private boolean enabled;
        private int threshold;
        private int current;
        private double ratio;
        private String status;
    }
}