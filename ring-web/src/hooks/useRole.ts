import { Session } from '@supabase/supabase-js'
import { useQuery } from '@tanstack/react-query'
import { supabase } from '../lib/supabase'

export interface UserProfile {
  id: string
  role: 'patient' | 'doctor' | string
  full_name: string | null
  email: string | null
}

export function useRole(session: Session) {
  return useQuery({
    queryKey: ['user-profile', session.user.id],
    queryFn: async (): Promise<UserProfile> => {
      const { data, error } = await supabase
        .from('user_profiles')
        .select('id, role, full_name, email')
        .eq('id', session.user.id)
        .single()
      if (error) throw error
      return data as UserProfile
    },
    staleTime: 5 * 60 * 1000,
  })
}

export function displayName(
  profile: UserProfile | undefined | null,
  userId: string,
): string {
  if (!profile) return userId.slice(0, 8) + '…'
  return profile.full_name?.trim() || profile.email || userId.slice(0, 8) + '…'
}
