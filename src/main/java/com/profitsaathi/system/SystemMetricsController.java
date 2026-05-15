package com.profitsaathi.system;

import com.sun.management.OperatingSystemMXBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
public class SystemMetricsController {

    private final OperatingSystemMXBean os =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        Runtime rt = Runtime.getRuntime();
        long heapMax = rt.maxMemory();
        long heapUsed = rt.totalMemory() - rt.freeMemory();
        long totalSystem = os.getTotalMemorySize();
        long freeSystem = os.getFreeMemorySize();

        Map<String, Object> cpu = new LinkedHashMap<>();
        cpu.put("availableProcessors", os.getAvailableProcessors());
        cpu.put("processCpuLoadPercent", toPercent(os.getProcessCpuLoad()));
        cpu.put("systemCpuLoadPercent", toPercent(os.getCpuLoad()));

        Map<String, Object> ram = new LinkedHashMap<>();
        ram.put("totalSystemBytes", totalSystem);
        ram.put("freeSystemBytes", freeSystem);
        ram.put("usedSystemBytes", totalSystem - freeSystem);
        ram.put("systemUsedPercent", totalSystem > 0
                ? round(((totalSystem - freeSystem) * 100.0) / totalSystem)
                : 0.0);
        ram.put("jvmHeapMaxBytes", heapMax);
        ram.put("jvmHeapUsedBytes", heapUsed);
        ram.put("jvmHeapUsedPercent", heapMax > 0
                ? round((heapUsed * 100.0) / heapMax)
                : 0.0);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cpu", cpu);
        body.put("ram", ram);
        return body;
    }

    private static double toPercent(double load) {
        if (Double.isNaN(load) || load < 0) return 0.0;
        return round(load * 100.0);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
