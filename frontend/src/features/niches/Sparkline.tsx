/** Vagas abertas por semana (12 semanas) em barras finas; o título repete os números para leitura sem cor. */
export function Sparkline({ values }: { values: number[] }) {
  const max = Math.max(1, ...values)
  const total = values.reduce((sum, value) => sum + value, 0)
  const width = values.length * 5
  return (
    <svg
      width={width}
      height={20}
      viewBox={`0 0 ${width} 20`}
      role="img"
      aria-label={`${total} vagas abertas nas últimas ${values.length} semanas`}
    >
      <title>{`Vagas por semana: ${values.join(', ')}`}</title>
      <line x1={0} x2={width} y1={19.5} y2={19.5} stroke="var(--border)" />
      {values.map((value, index) =>
        value === 0 ? null : (
          <rect
            key={index}
            x={index * 5}
            y={19 - Math.max(2, (value / max) * 18)}
            width={3}
            height={Math.max(2, (value / max) * 18)}
            rx={1}
            fill="var(--series-1)"
          />
        ),
      )}
    </svg>
  )
}
