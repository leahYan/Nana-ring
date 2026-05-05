import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts'
import { supabase } from '../lib/supabase'

type Range = '24h' | '7d' | '30d'

const RANGE_MS: Record<Range, number> = {
  '24h': 24 * 60 * 60 * 1000,
  '7d': 7 * 24 * 60 * 60 * 1000,
  '30d': 30 * 24 * 60 * 60 * 1000,
}

function formatTick(ms: number, range: Range): string {
  const d = new Date(ms)
  if (range === '24h') {
    return d.toLocaleTimeString('en-AU', { hour: '2-digit', minute: '2-digit', hour12: false })
  }
  return d.toLocaleDateString('en-AU', { month: 'short', day: 'numeric' })
}

interface Props { userId: string }

export default function SpO2Chart({ userId }: Props) {
  const [range, setRange] = useState<Range>('24h')
  const since = Date.now() - RANGE_MS[range]

  const { data, isLoading } = useQuery({
    queryKey: ['spo2-chart', userId, range],
    queryFn: async () => {
      const { data, error } = await supabase
        .from('ring_spo2')
        .select('device_timestamp, percent')
        .eq('user_id', userId)
        .gte('device_timestamp', since)
        .order('device_timestamp', { ascending: true })
      if (error) throw error
      return (data ?? []) as { device_timestamp: number; percent: number }[]
    },
  })

  return (
    <section className="bg-white rounded-2xl border border-stone-200 p-6 shadow-sm">
      <div className="flex items-center justify-between mb-5">
        <div>
          <h2 className="text-xl font-semibold text-stone-800">Blood Oxygen (SpO2)</h2>
          <p className="text-sm text-stone-500 mt-0.5">% SpO2</p>
        </div>
        <div className="flex gap-2">
          {(['24h', '7d', '30d'] as Range[]).map((r) => (
            <button
              key={r}
              onClick={() => setRange(r)}
              className={`min-h-[44px] px-4 rounded-xl text-base font-medium transition-colors ${
                range === r
                  ? 'bg-amber-600 text-white'
                  : 'bg-stone-100 text-stone-600 hover:bg-stone-200'
              }`}
            >
              {r}
            </button>
          ))}
        </div>
      </div>

      {isLoading && (
        <p className="text-base text-stone-400 py-14 text-center">Loading…</p>
      )}

      {!isLoading && (!data || data.length === 0) && (
        <p className="text-base text-stone-400 py-14 text-center italic">
          No SpO2 readings in the last {range}. Make sure the ring app is syncing.
        </p>
      )}

      {!isLoading && data && data.length > 0 && (
        <ResponsiveContainer width="100%" height={260}>
          <LineChart data={data} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#E7E5E4" />
            <XAxis
              dataKey="device_timestamp"
              type="number"
              domain={['dataMin', 'dataMax']}
              tickFormatter={(ms: number) => formatTick(ms, range)}
              tick={{ fill: '#78716C', fontSize: 13 }}
              tickCount={6}
            />
            <YAxis
              domain={[85, 100]}
              unit="%"
              tick={{ fill: '#78716C', fontSize: 13 }}
              width={50}
            />
            <Tooltip
              contentStyle={{ background: '#fff', border: '1px solid #E7E5E4', borderRadius: 12, fontSize: 14 }}
              labelFormatter={(ms: number) => new Date(ms).toLocaleString('en-AU', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false })}
              formatter={(val: number) => [`${val}% SpO2`, 'Blood Oxygen']}
            />
            <Line
              type="monotone"
              dataKey="percent"
              stroke="#059669"
              strokeWidth={2}
              dot={false}
              activeDot={{ r: 4, fill: '#059669' }}
            />
          </LineChart>
        </ResponsiveContainer>
      )}
    </section>
  )
}
