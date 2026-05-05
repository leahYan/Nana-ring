import { useQuery } from '@tanstack/react-query'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts'
import { supabase } from '../lib/supabase'

interface Props { userId: string }

export default function ActivityPanel({ userId }: Props) {
  const sevenDaysAgo = Date.now() - 7 * 24 * 60 * 60 * 1000

  const { data, isLoading } = useQuery({
    queryKey: ['activity-panel', userId],
    queryFn: async () => {
      const { data, error } = await supabase
        .from('ring_activity')
        .select('device_timestamp, steps')
        .eq('user_id', userId)
        .gte('device_timestamp', sevenDaysAgo)
        .order('device_timestamp', { ascending: true })
      if (error) throw error
      return (data ?? []) as { device_timestamp: number; steps: number }[]
    },
  })

  const chartData = (data ?? []).map((r) => ({
    date: new Date(r.device_timestamp).toLocaleDateString('en-AU', { month: 'short', day: 'numeric' }),
    steps: r.steps,
  }))

  return (
    <section className="bg-white rounded-2xl border border-stone-200 p-6 shadow-sm">
      <div className="mb-5">
        <h2 className="text-xl font-semibold text-stone-800">Daily Steps</h2>
        <p className="text-sm text-stone-500 mt-0.5">Last 7 days — steps per day</p>
      </div>

      {isLoading && (
        <p className="text-base text-stone-400 py-14 text-center">Loading…</p>
      )}

      {!isLoading && chartData.length === 0 && (
        <p className="text-base text-stone-400 py-14 text-center italic">
          No activity data in the last 7 days. Make sure the ring app is syncing.
        </p>
      )}

      {!isLoading && chartData.length > 0 && (
        <ResponsiveContainer width="100%" height={260}>
          <BarChart data={chartData} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#E7E5E4" vertical={false} />
            <XAxis dataKey="date" tick={{ fill: '#78716C', fontSize: 13 }} />
            <YAxis
              tick={{ fill: '#78716C', fontSize: 13 }}
              width={65}
              tickFormatter={(v: number) => v.toLocaleString()}
            />
            <Tooltip
              contentStyle={{ background: '#fff', border: '1px solid #E7E5E4', borderRadius: 12, fontSize: 14 }}
              formatter={(val: number) => [val.toLocaleString() + ' steps', 'Steps']}
            />
            <Bar dataKey="steps" fill="#D97706" radius={[4, 4, 0, 0]} name="Steps" />
          </BarChart>
        </ResponsiveContainer>
      )}
    </section>
  )
}
