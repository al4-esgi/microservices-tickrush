import { Injectable, OnModuleDestroy } from '@nestjs/common';
import { collectDefaultMetrics, Histogram, Registry } from 'prom-client';

type HttpLabels = 'method' | 'route' | 'status';

@Injectable()
export class MetricsService implements OnModuleDestroy {
  private readonly registry = new Registry();
  private readonly httpDuration: Histogram<HttpLabels>;

  constructor() {
    this.registry.setDefaultLabels({ application: 'payment-service' });
    collectDefaultMetrics({
      register: this.registry,
      prefix: 'payment_service_',
    });
    this.httpDuration = new Histogram<HttpLabels>({
      name: 'tickrush_http_request_duration_seconds',
      help: 'Duration des requetes HTTP du payment-service en secondes',
      labelNames: ['method', 'route', 'status'],
      buckets: [0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5],
      registers: [this.registry],
    });
  }

  observeHttpRequest(
    method: string,
    route: string,
    status: number,
    durationSeconds: number,
  ): void {
    this.httpDuration.observe(
      { method, route, status: String(status) },
      durationSeconds,
    );
  }

  metrics(): Promise<string> {
    return this.registry.metrics();
  }

  get contentType(): string {
    return this.registry.contentType;
  }

  onModuleDestroy(): void {
    this.registry.clear();
  }
}
