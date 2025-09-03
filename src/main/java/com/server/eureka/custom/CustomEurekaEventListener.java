package com.server.eureka.custom;

import com.server.eureka.alert.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.netflix.eureka.server.event.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomEurekaEventListener {
    
    private final AlertService alertService;
    private final ConcurrentHashMap<String, AtomicLong> appRegistrationCount = new ConcurrentHashMap<>();
    
    /**
     * ✅ 실제 존재하는 이벤트: 인스턴스 등록
     */
    @EventListener
    public void onInstanceRegistration(EurekaInstanceRegisteredEvent event) {
        String appName = event.getInstanceInfo().getAppName();
        String instanceId = event.getInstanceInfo().getInstanceId();
        
        // 등록 카운트 증가
        appRegistrationCount.computeIfAbsent(appName, k -> new AtomicLong(0)).incrementAndGet();
        
        log.info("📝 [REGISTRATION] {} - {} registered", appName, instanceId);
        
        // 첫 번째 인스턴스 등록 시 알림
        if (appRegistrationCount.get(appName).get() == 1) {
            alertService.sendAlert(
                AlertService.AlertType.INFO,
                "First Instance Registered",
                String.format("First instance of %s has been registered: %s", appName, instanceId)
            );
        }
    }
    
    /**
     * ✅ 실제 존재하는 이벤트: 인스턴스 해제
     */
    @EventListener
    public void onInstanceCancellation(EurekaInstanceCanceledEvent event) {
        String appName = event.getAppName();
        String instanceId = event.getServerId();
        
        log.warn("🗑️  [CANCELLATION] {} - {} canceled", appName, instanceId);
        
        // 마지막 인스턴스 해제 시 크리티컬 알림
        long remainingInstances = getCurrentInstanceCount(appName);
        if (remainingInstances == 0) {
            alertService.sendAlert(
                AlertService.AlertType.CRITICAL,
                "Last Instance Canceled",
                String.format("Last instance of %s has been canceled: %s", appName, instanceId)
            );
        }
    }
    
    /**
     * ✅ 실제 존재하는 이벤트: 인스턴스 갱신 (Heartbeat)
     */
    @EventListener
    public void onInstanceRenewal(EurekaInstanceRenewedEvent event) {
        String appName = event.getInstanceInfo().getAppName();
        log.debug("🔄 [RENEWAL] {} renewed", appName);
        
        // 특정 애플리케이션 모니터링
        if ("critical-service".equals(appName)) {
            monitorCriticalService(event);
        }
    }
    
    // ❌ 제거: 존재하지 않는 이벤트들
    // EurekaInstanceStatusChangedEvent - 존재하지 않음
    // EurekaPeerAwareStatusChangeEvent - 존재하지 않음
    
    private void monitorCriticalService(EurekaInstanceRenewedEvent event) {
        // 크리티컬 서비스에 대한 특별 모니터링
        log.info("🔍 Critical service {} is healthy", event.getInstanceInfo().getAppName());
    }
    
    private long getCurrentInstanceCount(String appName) {
        // 현재 등록된 인스턴스 수 계산
        return appRegistrationCount.getOrDefault(appName, new AtomicLong(0)).get();
    }
}