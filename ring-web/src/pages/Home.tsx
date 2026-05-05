import { Link } from 'react-router-dom'

const patientFeatures = [
  {
    icon: '💚',
    title: 'Continuous Monitoring',
    desc: 'Your ring quietly tracks your heart rate, oxygen levels, temperature, and sleep — 24 hours a day.',
  },
  {
    icon: '🕊️',
    title: 'Peace of Mind',
    desc: 'Know that your health is being watched over, so you and your family can focus on living.',
  },
  {
    icon: '🏡',
    title: 'Stay Independent',
    desc: 'Live confidently at home with your doctor keeping a quiet eye on your wellbeing.',
  },
  {
    icon: '👩‍⚕️',
    title: 'Connected to Your Care Team',
    desc: 'Your doctor stays informed without you needing to make extra calls or appointments.',
  },
]

const doctorFeatures = [
  {
    icon: '📊',
    title: 'Proactive Monitoring',
    desc: "See your patients' real-time health trends from anywhere, on any device.",
  },
  {
    icon: '📈',
    title: 'Spot Changes Early',
    desc: 'Identify shifts in heart rate, SpO2, blood pressure, and sleep patterns before they escalate.',
  },
  {
    icon: '⏱️',
    title: 'Less Admin, More Care',
    desc: 'Spend less time gathering data and more time on meaningful patient conversations.',
  },
  {
    icon: '🗂️',
    title: 'All Patients in One View',
    desc: 'Your full patient list with the metrics that matter most, always up to date.',
  },
]

const steps = [
  {
    icon: '💍',
    step: '1',
    title: 'Wear the ring',
    desc: 'Put it on like any ring. It works quietly in the background throughout your day and night.',
  },
  {
    icon: '📡',
    step: '2',
    title: 'Data syncs automatically',
    desc: 'Your health readings sync to the dashboard via the Nana Ring app on your phone.',
  },
  {
    icon: '🩺',
    step: '3',
    title: 'Your doctor monitors remotely',
    desc: 'Your doctor can see your trends and reach out if anything needs attention.',
  },
]

export default function Home() {
  return (
    <div className="min-h-screen bg-stone-50">
      {/* Navigation */}
      <nav className="bg-white border-b border-stone-200 sticky top-0 z-10">
        <div className="max-w-6xl mx-auto px-6 py-4 flex items-center justify-between">
          <span className="text-xl font-bold text-amber-700">Nana Ring</span>
          <Link
            to="/login"
            className="inline-flex items-center min-h-[44px] px-6 bg-amber-600 hover:bg-amber-700 text-white text-base font-semibold rounded-xl transition-colors"
          >
            Sign In
          </Link>
        </div>
      </nav>

      {/* Hero */}
      <section className="bg-amber-50 border-b border-amber-100 py-24 px-6 text-center">
        <h1 className="text-4xl font-bold text-stone-900 mb-5 leading-tight">
          Health in your hands.
          <br />
          <span className="text-amber-700">Peace of mind for everyone.</span>
        </h1>
        <p className="text-xl text-stone-600 max-w-2xl mx-auto mb-10 leading-relaxed">
          Nana Ring continuously monitors your vital signs and keeps your doctor informed —
          so you can live confidently and independently.
        </p>
        <Link
          to="/login"
          className="inline-flex items-center min-h-[52px] px-10 bg-amber-600 hover:bg-amber-700 text-white text-lg font-bold rounded-2xl transition-colors shadow-md"
        >
          Sign In to Your Dashboard
        </Link>
      </section>

      {/* For Patients */}
      <section className="py-20 px-6">
        <div className="max-w-6xl mx-auto">
          <div className="text-center mb-12">
            <h2 className="text-3xl font-bold text-stone-900 mb-3">For Patients</h2>
            <p className="text-xl text-stone-600">Everything you need, quietly working for you.</p>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            {patientFeatures.map((f) => (
              <div key={f.title} className="bg-white rounded-2xl border border-stone-200 p-6 shadow-sm">
                <div className="text-4xl mb-4">{f.icon}</div>
                <h3 className="text-xl font-semibold text-stone-900 mb-2">{f.title}</h3>
                <p className="text-base text-stone-600 leading-relaxed">{f.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* For Doctors */}
      <section className="py-20 px-6 bg-white border-y border-stone-200">
        <div className="max-w-6xl mx-auto">
          <div className="text-center mb-12">
            <h2 className="text-3xl font-bold text-stone-900 mb-3">For Doctors</h2>
            <p className="text-xl text-stone-600">Remote monitoring that fits your workflow.</p>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            {doctorFeatures.map((f) => (
              <div key={f.title} className="bg-stone-50 rounded-2xl border border-stone-200 p-6 shadow-sm">
                <div className="text-4xl mb-4">{f.icon}</div>
                <h3 className="text-xl font-semibold text-stone-900 mb-2">{f.title}</h3>
                <p className="text-base text-stone-600 leading-relaxed">{f.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* How It Works */}
      <section className="py-20 px-6 bg-amber-50 border-b border-amber-100">
        <div className="max-w-4xl mx-auto">
          <h2 className="text-3xl font-bold text-stone-900 mb-14 text-center">How It Works</h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-10">
            {steps.map((s) => (
              <div key={s.step} className="text-center">
                <div className="text-6xl mb-5">{s.icon}</div>
                <div className="inline-flex items-center justify-center w-8 h-8 bg-amber-600 text-white text-base font-bold rounded-full mb-3">
                  {s.step}
                </div>
                <h3 className="text-xl font-semibold text-stone-900 mb-3">{s.title}</h3>
                <p className="text-base text-stone-600 leading-relaxed">{s.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="bg-stone-800 py-10 px-6 text-center">
        <p className="text-xl font-bold text-white mb-3">Nana Ring</p>
        <p className="text-base text-stone-400 max-w-lg mx-auto leading-relaxed">
          Your privacy is important to us. All health data is securely encrypted and accessible
          only to you and your authorised care team.
        </p>
      </footer>
    </div>
  )
}
