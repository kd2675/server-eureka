package com.server.eureka.observability;

import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.cluster.PeerEurekaNode;
import com.netflix.appinfo.InstanceInfo;
import io.micrometer.core.instrument.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdvancedMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    private final EurekaServerContext eurekaServerContext;
    
    // 메트릭 캐시 (중복 등록 방지용)
    private final ConcurrentHashMap<String, Boolean> registeredMetrics = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initAdvancedMetrics() {
        try {
            // ✅ MeterRegistry에 직접 등록하는 방식으로 변경
            meterRegistry.gauge("eureka.registry.size.bytes", this, AdvancedMetricsCollector::getRegistrySizeBytes);
            meterRegistry.gauge("eureka.peer.nodes.total", this, AdvancedMetricsCollector::getTotalPeerNodes);
            meterRegistry.gauge("eureka.applications.total", this, AdvancedMetricsCollector::getTotalApplications);
            meterRegistry.gauge("eureka.instances.total", this, AdvancedMetricsCollector::getTotalInstances);
            meterRegistry.gauge("eureka.memory.utilization.ratio", this, AdvancedMetricsCollector::getMemoryUtilization);
            
            log.info("✅ Advanced metrics collector initialized");
            
        } catch (Exception e) {
            log.error("❌ Failed to initialize advanced metrics", e);
        }
    }
    
    @Scheduled(fixedDelay = 30000) // 30초마다
    public void collectAdvancedMetrics() {
        try {
            collectApplicationMetrics();
            collectPerformanceMetrics();
            logCurrentMetrics();
        } catch (Exception e) {
            log.error("Error collecting advanced metrics", e);
        }
    }
    
    /**
     * ✅ 애플리케이션별 메트릭 수집 (실제 작동하는 방식)
     */
    private void collectApplicationMetrics() {
        try {
            if (eurekaServerContext.getRegistry() == null || 
                eurekaServerContext.getRegistry().getApplications() == null) {
                return;
            }
            
            eurekaServerContext.getRegistry().getApplications()
                .getRegisteredApplications()
                .forEach(this::collectSingleApplicationMetrics);
                
        } catch (Exception e) {
            log.warn("Error collecting application metrics", e);
        }
    }
    
    private void collectSingleApplicationMetrics(com.netflix.discovery.shared.Application application) {
        try {
            String appName = application.getName().toLowerCase();
            
            // 상태별 인스턴스 카운트 (안전한 방식)
            long upCount = 0;
            long downCount = 0;
            long startingCount = 0;
            long outOfServiceCount = 0;
            
            for (InstanceInfo instance : application.getInstancesAsIsFromEureka()) {
                switch (instance.getStatus()) {
                    case UP:
                        upCount++;
                        break;
                    case DOWN:
                        downCount++;
                        break;
                    case STARTING:
                        startingCount++;
                        break;
                    case OUT_OF_SERVICE:
                        outOfServiceCount++;
                        break;
                    default:
                        break;
                }
            }
            
            // ✅ 안전한 메트릭 등록 방식
            String upMetricKey = "eureka.app.instances.up." + sanitizeMetricName(appName);
            if (!registeredMetrics.containsKey(upMetricKey)) {
                final long finalUpCount = upCount;
                meterRegistry.gauge(upMetricKey, application, app -> (double) getAppInstanceCount(app, InstanceInfo.InstanceStatus.UP));
                registeredMetrics.put(upMetricKey, true);
            }
            
            String downMetricKey = "eureka.app.instances.down." + sanitizeMetricName(appName);
            if (!registeredMetrics.containsKey(downMetricKey)) {
                meterRegistry.gauge(downMetricKey, application, app -> (double) getAppInstanceCount(app, InstanceInfo.InstanceStatus.DOWN));
                registeredMetrics.put(downMetricKey, true);
            }
            
            // 로그로 상태 정보 출력
            log.debug("📊 [APP_METRICS] {} - UP:{} DOWN:{} STARTING:{} OUT_OF_SERVICE:{}", 
                    appName, upCount, downCount, startingCount, outOfServiceCount);
                    
        } catch (Exception e) {
            log.warn("Error collecting metrics for application: {}", application.getName(), e);
        }
    }
    
    /**
     * ✅ 성능 메트릭 수집 (안전한 방식)
     */
    private void collectPerformanceMetrics() {
        try {
            // JVM 메모리 메트릭만 수집 (확실히 작동하는 것들)
            logMemoryMetrics();
            logRegistryStats();
            
        } catch (Exception e) {
            log.warn("Error collecting performance metrics", e);
        }
    }
    
    private void logMemoryMetrics() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double memoryUtilization = maxMemory > 0 ? (double) usedMemory / maxMemory : 0.0;
        
        log.debug("💾 [MEMORY] Used: {}MB / Max: {}MB ({}%)", 
                usedMemory / 1024 / 1024,
                maxMemory / 1024 / 1024,
                String.format("%.1f", memoryUtilization * 100));
    }
    
    private void logRegistryStats() {
        try {
            int totalApps = getTotalApplications().intValue();
            int totalInstances = getTotalInstances().intValue();
            
            log.debug("📋 [REGISTRY] Applications: {}, Total Instances: {}", totalApps, totalInstances);
            
        } catch (Exception e) {
            log.debug("Cannot access registry stats", e);
        }
    }
    
    /**
     * ✅ 현재 메트릭 상태를 로그로 출력
     */
    private void logCurrentMetrics() {
        try {
            double registrySize = getRegistrySizeBytes();
            double totalPeers = getTotalPeerNodes();
            double totalApps = getTotalApplications();
            double totalInstances = getTotalInstances();
            double memoryUtil = getMemoryUtilization();

            log.info("📈 [METRICS_SUMMARY] Registry: {}KB | Peers: {} | Apps: {} | Instances: {} | Memory: {}%",
                    (long)(registrySize / 1024),
                    (long)totalPeers,
                    (long)totalApps,
                    (long)totalInstances,
                    String.format("%.1f", memoryUtil * 100));
                    
        } catch (Exception e) {
            log.debug("Error logging current metrics", e);
        }
    }
    
    /**
     * ✅ 메트릭 계산 메서드들 (실제 작동하는 방식)
     */
    public Double getRegistrySizeBytes() {
        try {
            if (eurekaServerContext.getRegistry() == null || 
                eurekaServerContext.getRegistry().getApplications() == null) {
                return 0.0;
            }
            
            return (double) eurekaServerContext.getRegistry().getApplications()
                .getRegisteredApplications().stream()
                .mapToInt(app -> app.getInstances().size() * 1024) // 인스턴스당 ~1KB 추정
                .sum();
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    public Double getTotalPeerNodes() {
        try {
            if (eurekaServerContext.getPeerEurekaNodes() == null) {
                return 0.0;
            }
            return (double) eurekaServerContext.getPeerEurekaNodes().getPeerEurekaNodes().size();
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    public Double getTotalApplications() {
        try {
            if (eurekaServerContext.getRegistry() == null || 
                eurekaServerContext.getRegistry().getApplications() == null) {
                return 0.0;
            }
            return (double) eurekaServerContext.getRegistry().getApplications()
                .getRegisteredApplications().size();
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    public Double getTotalInstances() {
        try {
            if (eurekaServerContext.getRegistry() == null || 
                eurekaServerContext.getRegistry().getApplications() == null) {
                return 0.0;
            }
            return (double) eurekaServerContext.getRegistry().getApplications()
                .getRegisteredApplications().stream()
                .mapToInt(app -> app.getInstances().size())
                .sum();
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    public Double getMemoryUtilization() {
        try {
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            return maxMemory > 0 ? (double) usedMemory / maxMemory : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    /**
     * ✅ 애플리케이션별 상태별 인스턴스 수 계산
     */
    private long getAppInstanceCount(com.netflix.discovery.shared.Application application, InstanceInfo.InstanceStatus status) {
        try {
            return application.getInstancesAsIsFromEureka().stream()
                .filter(instance -> instance.getStatus() == status)
                .count();
        } catch (Exception e) {
            return 0L;
        }
    }
    
    /**
     * ✅ 메트릭 이름 정규화
     */
    private String sanitizeMetricName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }
}