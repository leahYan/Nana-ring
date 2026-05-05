interface Props {
  title: string
  value: string | number | undefined | null
  unit: string
  timestamp?: number | null
  emptyMessage?: string
}

function formatTimestamp(ms: number): string {
  return new Date(ms).toLocaleString('en-AU', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

export default function MetricCard({ title, value, unit, timestamp, emptyMessage }: Props) {
  const hasValue = value !== undefined && value !== null

  return (
    <div className="bg-white rounded-2xl border border-stone-200 p-5 shadow-sm">
      <p className="text-sm font-semibold text-stone-500 uppercase tracking-wide">{title}</p>
      {hasValue ? (
        <>
          <p className="mt-2 text-3xl font-bold text-stone-900 leading-tight">
            {typeof value === 'number' ? value.toLocaleString() : value}
            <span className="text-base font-normal text-stone-500 ml-1.5">{unit}</span>
          </p>
          {timestamp != null && (
            <p className="mt-1.5 text-sm text-stone-400">{formatTimestamp(timestamp)}</p>
          )}
        </>
      ) : (
        <p className="mt-3 text-base text-stone-400 italic leading-snug">
          {emptyMessage ?? 'No data yet. Make sure the ring app is syncing.'}
        </p>
      )}
    </div>
  )
}
