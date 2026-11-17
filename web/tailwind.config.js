/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  darkMode: 'media',
  theme: {
    extend: {
      fontFamily: {
        display: ['"Plus Jakarta Sans"', 'sans-serif'],
        body:    ['"DM Sans"', 'sans-serif'],
        sans:    ['"DM Sans"', 'sans-serif'],
      },
      colors: {
        brand: {
          50:  '#edfaf3',
          100: '#d3f3e3',
          200: '#aae5ca',
          300: '#73cfab',
          400: '#3fb389',
          500: '#1e9870',
          600: '#127a5a',
          700: '#0E5B33',
          800: '#0c4a2b',
          900: '#0a3d24',
        },
        accent: '#FFD43B',
        surface: {
          DEFAULT: '#ffffff',
          muted:   '#F6F7F2',
          border:  '#E8EAE4',
        },
        ink: {
          DEFAULT: '#111816',
          muted:   '#5a6360',
          subtle:  '#9aa39f',
        },
        status: {
          pending:    { bg: '#FEF9C3', text: '#92400E', dot: '#F59E0B' },
          preparing:  { bg: '#DBEAFE', text: '#1E3A5F', dot: '#3B82F6' },
          on_the_way: { bg: '#EDE9FE', text: '#3B0764', dot: '#8B5CF6' },
          delivered:  { bg: '#D1FAE5', text: '#064E3B', dot: '#10B981' },
          cancelled:  { bg: '#FEE2E2', text: '#7F1D1D', dot: '#EF4444' },
        },
      },
      borderRadius: {
        card:   '12px',
        input:  '8px',
        badge:  '6px',
        pill:   '9999px',
      },
      boxShadow: {
        card:  '0 1px 3px 0 rgb(0 0 0 / .06), 0 1px 2px -1px rgb(0 0 0 / .06)',
        modal: '0 20px 60px -15px rgb(0 0 0 / .18)',
        focus: '0 0 0 3px rgb(14 91 51 / .25)',
      },
      keyframes: {
        'fade-in': { from: { opacity: 0, transform: 'translateY(4px)' }, to: { opacity: 1, transform: 'translateY(0)' } },
        pulse2:    { '0%,100%': { opacity: 1 }, '50%': { opacity: .4 } },
      },
      animation: {
        'fade-in': 'fade-in .18s ease-out both',
        skeleton:  'pulse2 1.4s ease-in-out infinite',
      },
    },
  },
  plugins: [],
}
