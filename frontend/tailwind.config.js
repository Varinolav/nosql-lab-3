/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Manrope', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        display: ['Unbounded', 'Manrope', 'sans-serif'],
      },
      boxShadow: {
        card: '0 24px 70px rgba(38, 26, 33, 0.08)',
        pop: '6px 6px 0 #241b20',
      },
    },
  },
  plugins: [],
}
