import { useQuery } from '@tanstack/react-query'
import { supabase } from '../lib/supabase'
import MetricCard from './MetricCard'
import HeartRateChart from './HeartRateChart'
import SpO2Chart from './SpO2Chart'
import BloodPressureChart from './BloodPressureChart'
import SleepPanel from './SleepPanel'
import ActivityPanel from './ActivityPanel'
import SportSessionsPanel from './SportSessionsPanel'

interface Props {
  userId: string
}

export default function HealthMetrics({ userId }: Props) {
  const { data: latestHR } = useQuery({
    queryKey: ['latest-hr', userId],
    queryFn: async () => {
      const { data, error } = await supabase
        .from('ring_heart_rate')
        .select('bpm, device_timestamp')
        .eq('user_id', userId)
        .order('device_timestamp', { ascending: false })
        .limit(1)
        .maybeSingle()
      if (error) throw error
      return data as { bpm: number; device_timestamp: number } | null
    },
  })

  const { data: latestSpO2 } = useQuery({
    queryKey: ['latest-spo2', userId],
    queryFn: async () => {
      const { data, error } = await supabase
        .from('ring_spo2')
        .select('percent, device_timestamp')
        .eq('user_id', userId)
        .order('device_timestamp', { ascending: false })
        .limit(1)
        .maybeSingle()
      if (error) throw error
      return data as { percent: number; device_timestamp: number } | null
    },
  })

  const { data: latestBP } = useQuery({
    queryKey: ['latest-bp', userId],
    queryFn: async () => {
      const { data, error } = await supabase
        .from('ring_blood_pressure')
        .select('systolic, diastolic, device_timestamp')
        .eq('user_id', userId)
        .order('device_timestamp', { ascending: false })
        .limit(1)
        .maybeSingle()
      if (error) throw error
      return data as { systolic: number; diastolic: number; device_timestamp: number } | null
    },
  })

  const { data: latestTemp } = useQuery({
    queryKey: ['latest-temp', userId],
    queryFn: async () => {
      const { data, error } = await supabase
        .from('ring_temperature')
        .select('celsius_primary, device_timestamp')
        .eq('user_id', userId)
        .order('device_timestamp', { ascending: false })
        .limit(1)
        .maybeSingle()
      if (error) throw error
      return data as { celsius_primary: number; device_timestamp: number } | null
    },
  })

  const { data: latestActivity } = useQuery({
    queryKey: ['latest-activity', userId],
    queryFn: async () => {
      const { data, error } = await supabase
        .from('ring_activity')
        .select('steps, device_timestamp')
        .eq('user_id', userId)
        .order('device_timestamp', { ascending: false })
        .limit(1)
        .maybeSingle()
      if (error) throw error
      return data as { steps: number; device_timestamp: number } | null
    },
  })

  const { data: latestSleep } = useQuery({
    queryKey: ['latest-sleep-card', userId],
    queryFn: async () => {
      const { data, error } = await supabase
        .from('ring_sleep')
        .select('total_minutes, start_timestamp')
        .eq('user_id', userId)
        .order('start_timestamp', { ascending: false })
        .limit(1)
        .maybeSingle()
      if (error) throw error
      return data as { total_minutes: number; start_timestamp: number } | null
    },
  })

  const bpValue = latestBP
    ? `${latestBP.systolic}/${latestBP.diastolic}`
    : undefined
  const tempValue = latestTemp
    ? Number(latestTemp.celsius_primary).toFixed(1)
    : undefined

  return (
    <div className="space-y-8">
      <div className="grid grid-cols-2 lg:grid-cols-3 gap-4">
        <MetricCard
          title="Heart Rate"
          value={latestHR?.bpm ?? null}
          unit="bpm"
          timestamp={latestHR?.device_timestamp}
        />
        <MetricCard
          title="Blood Oxygen (SpO2)"
          value={latestSpO2?.percent ?? null}
          unit="% SpO2"
          timestamp={latestSpO2?.device_timestamp}
        />
        <MetricCard
          title="Blood Pressure"
          value={bpValue ?? null}
          unit="mmHg"
          timestamp={latestBP?.device_timestamp}
        />
        <MetricCard
          title="Temperature"
          value={tempValue ?? null}
          unit="°C"
          timestamp={latestTemp?.device_timestamp}
        />
        <MetricCard
          title="Steps Today"
          value={latestActivity?.steps ?? null}
          unit="steps"
          timestamp={latestActivity?.device_timestamp}
        />
        <MetricCard
          title="Sleep Last Night"
          value={latestSleep?.total_minutes ?? null}
          unit="minutes"
          timestamp={latestSleep?.start_timestamp}
        />
      </div>

      <HeartRateChart userId={userId} />
      <SpO2Chart userId={userId} />
      <BloodPressureChart userId={userId} />
      <SleepPanel userId={userId} />
      <ActivityPanel userId={userId} />
      <SportSessionsPanel />
    </div>
  )
}
