package com.ssa.gateway.config;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.loadbalancer.core.NoopServiceInstanceListSupplier;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;


/**
 * 自定义轮询负载均衡器
 *
 * @author ssa
 */
public class RoundRobinLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    private final AtomicInteger position = new AtomicInteger();
    private final String serviceId;
    private final ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider;

    public RoundRobinLoadBalancer(ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider,
            String serviceId) {
        this.serviceInstanceListSupplierProvider = serviceInstanceListSupplierProvider;
        this.serviceId = serviceId;
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        ServiceInstanceListSupplier supplier = serviceInstanceListSupplierProvider
                .getIfAvailable(NoopServiceInstanceListSupplier::new);
        return supplier.get(request)
                .next()
                .map(serviceInstances -> {
                    if (serviceInstances == null || serviceInstances.isEmpty()) {
                        return new EmptyResponse();
                    }
                    int pos = Math.abs(position.incrementAndGet());
                    ServiceInstance instance = serviceInstances.get(pos % serviceInstances.size());
                    return new DefaultResponse(instance);
                });
    }
}