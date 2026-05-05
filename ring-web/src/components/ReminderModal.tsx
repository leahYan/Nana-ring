interface Props {
  doctorName: string
  onConfirm: () => void
  onCancel: () => void
  isLoading: boolean
}

export default function ReminderModal({ doctorName, onConfirm, onCancel, isLoading }: Props) {
  const message = `Dr. ${doctorName} is suggesting you book a 15-minute consultation with them to discuss your latest health changes.`

  return (
    <div
      className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4"
      onClick={onCancel}
    >
      <div
        className="bg-white rounded-2xl p-8 max-w-md w-full shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 className="text-2xl font-bold text-stone-900 mb-4">Send Consultation Reminder</h2>
        <p className="text-base text-stone-600 mb-3">The following message will be sent to your patient:</p>
        <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 mb-6">
          <p className="text-base text-amber-900 leading-relaxed">{message}</p>
        </div>
        <div className="flex gap-3">
          <button
            onClick={onCancel}
            disabled={isLoading}
            className="flex-1 min-h-[48px] bg-stone-100 hover:bg-stone-200 disabled:opacity-50 text-stone-700 text-base font-medium rounded-xl transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={onConfirm}
            disabled={isLoading}
            className="flex-1 min-h-[48px] bg-amber-600 hover:bg-amber-700 disabled:opacity-60 text-white text-base font-semibold rounded-xl transition-colors"
          >
            {isLoading ? 'Sending…' : 'Send Reminder'}
          </button>
        </div>
      </div>
    </div>
  )
}
