/**
 * CalcSuits Phase 4.1 — Shared POC Metrics & Statistics Helper
 * Computes min / p50 / p95 / max latency, granular mathematical feature accuracy
 * (symbols, exponents, fractions, roots, grouping), and separates OCR engine errors
 * from normalizer/AST errors.
 */

export function percentile(sortedValues, p) {
  if (!sortedValues.length) return 0;
  const idx = Math.ceil((p / 100) * sortedValues.length) - 1;
  return sortedValues[Math.max(0, Math.min(sortedValues.length - 1, idx))];
}

export function latencyStats(rows) {
  const vals = rows.map((r) => r.elapsed_ms).sort((a, b) => a - b);
  if (!vals.length) return { count: 0, min_ms: 0, p50_ms: 0, p95_ms: 0, max_ms: 0, avg_ms: 0 };
  const sum = vals.reduce((a, b) => a + b, 0);
  return {
    count: vals.length,
    min_ms: vals[0],
    p50_ms: percentile(vals, 50),
    p95_ms: percentile(vals, 95),
    max_ms: vals[vals.length - 1],
    avg_ms: Math.round(sum / vals.length),
  };
}

/**
 * Classifies failure origin to separate OCR engine errors from AST/normalizer errors:
 * - PASS: semantic exact match after AST normalization
 * - ENGINE_ERROR: raw OCR dropped/corrupted digits, operators, structure, or hallucinated
 * - NORMALIZER_ERROR: raw OCR contains mathematically valid expression but AST parser/serializer failed
 */
export function classifyFailureOrigin(semanticOk, rawOcr, astRes) {
  if (semanticOk) return "PASS";
  if (!rawOcr || !rawOcr.trim()) return "ENGINE_ERROR_EMPTY";
  // Heuristic check: if AST failed to parse, check whether raw OCR had obvious corruption/hallucination
  if (!astRes.ok) {
    if (/\\newcommand|\\begin\{array\}|##|\n\n/.test(rawOcr)) {
      return "ENGINE_ERROR_HALLUCINATION";
    }
    if (/[a-zA-Z]{3,}/.test(rawOcr.replace(/\\(frac|sqrt|times|mathrm|mathbf|text|cdot|div|left|right|chi)/g, ""))) {
      return "ENGINE_ERROR_CORRUPTED_TEXT";
    }
    return "ENGINE_OR_PARSE_ERROR";
  }
  return "ENGINE_ERROR_SEMANTIC_MISMATCH";
}

export function buildDetailedSummary(engineName, rows, meta = {}) {
  const byCategory = {};
  const byVariant = {};
  const byExpression = {};
  const granular = {
    symbols: { total: 0, passed: 0 },
    exponents: { total: 0, passed: 0 },
    fractions: { total: 0, passed: 0 },
    roots: { total: 0, passed: 0 },
    grouping: { total: 0, passed: 0 },
  };
  const errorOrigins = {};

  for (const r of rows) {
    errorOrigins[r.error_origin] = (errorOrigins[r.error_origin] || 0) + 1;

    byCategory[r.category] ??= { total: 0, passed: 0, rows: [] };
    byCategory[r.category].total++;
    if (r.semantic_exact) byCategory[r.category].passed++;
    byCategory[r.category].rows.push(r);

    byVariant[r.variant] ??= { total: 0, passed: 0, rows: [] };
    byVariant[r.variant].total++;
    if (r.semantic_exact) byVariant[r.variant].passed++;
    byVariant[r.variant].rows.push(r);

    byExpression[r.expr_id] ??= {
      total: 0,
      passed: 0,
      printed_pass: 0,
      printed_total: 0,
      d2_pass: 0,
      d2_total: 0,
      hw_pass: 0,
      hw_total: 0,
    };
    byExpression[r.expr_id].total++;
    if (r.semantic_exact) byExpression[r.expr_id].passed++;
    if (r.category === "printed") {
      byExpression[r.expr_id].printed_total++;
      if (r.semantic_exact) byExpression[r.expr_id].printed_pass++;
    } else if (r.category === "printed_2d") {
      byExpression[r.expr_id].d2_total++;
      if (r.semantic_exact) byExpression[r.expr_id].d2_pass++;
    } else {
      byExpression[r.expr_id].hw_total++;
      if (r.semantic_exact) byExpression[r.expr_id].hw_pass++;
    }

    const g = r.granular || {};
    if (g.symbols_ok !== null && g.symbols_ok !== undefined) {
      granular.symbols.total++;
      if (g.symbols_ok) granular.symbols.passed++;
    }
    if (g.exponent_ok !== null && g.exponent_ok !== undefined) {
      granular.exponents.total++;
      if (g.exponent_ok) granular.exponents.passed++;
    }
    if (g.fraction_ok !== null && g.fraction_ok !== undefined) {
      granular.fractions.total++;
      if (g.fraction_ok) granular.fractions.passed++;
    }
    if (g.root_ok !== null && g.root_ok !== undefined) {
      granular.roots.total++;
      if (g.root_ok) granular.roots.passed++;
    }
    if (g.grouping_ok !== null && g.grouping_ok !== undefined) {
      granular.grouping.total++;
      if (g.grouping_ok) granular.grouping.passed++;
    }
  }

  for (const k of Object.keys(byCategory)) {
    const c = byCategory[k];
    c.accuracy_pct = Number(((c.passed / c.total) * 100).toFixed(1));
    c.latency = latencyStats(c.rows);
    delete c.rows;
  }
  for (const k of Object.keys(byVariant)) {
    const v = byVariant[k];
    v.accuracy_pct = Number(((v.passed / v.total) * 100).toFixed(1));
    v.latency = latencyStats(v.rows);
    delete v.rows;
  }
  for (const k of Object.keys(granular)) {
    const g = granular[k];
    g.accuracy_pct = g.total > 0 ? Number(((g.passed / g.total) * 100).toFixed(1)) : null;
  }

  const totalPassed = rows.filter((r) => r.semantic_exact).length;
  return {
    engine: engineName,
    total_images: rows.length,
    total_passed: totalPassed,
    overall_accuracy_pct: rows.length ? Number(((totalPassed / rows.length) * 100).toFixed(1)) : 0,
    latency: latencyStats(rows),
    ...meta,
    error_origins: errorOrigins,
    granular_metrics: granular,
    by_category: byCategory,
    by_variant: byVariant,
    by_expression: byExpression,
  };
}
