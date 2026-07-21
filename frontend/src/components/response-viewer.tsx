import { Badge } from '@/components/ui/badge'
import type { ApiResult } from '@/lib/types'

export function ResponseViewer({
  result,
  pending,
}: {
  result?: ApiResult
  pending?: boolean
}) {
  if (pending) {
    return <p className="text-sm text-muted-foreground">Requête en cours…</p>
  }
  if (!result) {
    return <p className="text-sm text-muted-foreground">Aucune requête envoyée.</p>
  }
  return (
    <div className="space-y-2">
      <Badge variant={result.ok ? 'success' : 'destructive'}>HTTP {result.status}</Badge>
      <pre className="max-h-64 overflow-auto rounded-md bg-muted p-3 text-xs leading-relaxed">
        {typeof result.data === 'string'
          ? result.data || '(réponse vide)'
          : JSON.stringify(result.data, null, 2)}
      </pre>
    </div>
  )
}
