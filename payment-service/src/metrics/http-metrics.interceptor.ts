import {
  CallHandler,
  ExecutionContext,
  Injectable,
  NestInterceptor,
} from '@nestjs/common';
import type { Request, Response } from 'express';
import { Observable } from 'rxjs';
import { MetricsService } from './metrics.service';

@Injectable()
export class HttpMetricsInterceptor implements NestInterceptor {
  constructor(private readonly metrics: MetricsService) {}

  intercept(context: ExecutionContext, next: CallHandler): Observable<unknown> {
    if (context.getType() !== 'http') {
      return next.handle();
    }

    const request = context.switchToHttp().getRequest<Request>();
    const response = context.switchToHttp().getResponse<Response>();
    const startedAt = process.hrtime.bigint();

    response.once('finish', () => {
      const durationSeconds =
        Number(process.hrtime.bigint() - startedAt) / 1_000_000_000;
      this.metrics.observeHttpRequest(
        request.method,
        this.routeLabel(request),
        response.statusCode,
        durationSeconds,
      );
    });

    return next.handle();
  }

  private routeLabel(request: Request): string {
    const route = request.route as { path?: unknown } | undefined;
    const path = route?.path;
    if (typeof path !== 'string') {
      return 'unmatched';
    }
    const suffix = path === '/' ? '' : path;
    return `${request.baseUrl}${suffix}` || '/';
  }
}
