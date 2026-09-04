#!/usr/bin/env node
/**
 * Render the cross-version comparison table for the stress-test job summary.
 *
 * The suite's own gates are *within-run*: every step is measured at 100k and at
 * 1M and the ratio between them is asserted against a scaling-class bound. That
 * catches a step that starts scaling with the dataset, and it is blind to the
 * thing a release actually needs to know — "is this version slower than the one
 * before it, on the same step, at the same size". This script answers that one,
 * by collating stats.json artifacts from earlier runs.
 *
 * Comparability is not assumed. Each baseline carries the harness version and
 * the step fingerprint that produced it (see HARNESS_VERSION in common/utils),
 * and a baseline that disagrees with the current run on either is still shown
 * but marked: its deltas are measuring a harness change as well as a core one.
 *
 * Failed runs are uploaded as baselines too, because success-only upload starves
 * exactly the tags that need a history most. They are read at step granularity
 * rather than trusted or discarded wholesale: a step that failed or timed out
 * contributes no value, and a step that ran *after* an earlier failure in the
 * same run is shown but never flagged as a regression, since the failure may be
 * what made it slow.
 *
 * Input is a manifest describing what was downloaded; the workflow builds it.
 *   node scripts/compare-baselines.mjs <current stats.json> <manifest.json>
 * Manifest entries: { label, tag, dir, createdAt?, branch?, runUrl? }
 * Output: markdown on stdout.
 */
import { readFileSync, existsSync } from 'fs';
import { join } from 'path';

const [, , currentPath, manifestPath] = process.argv;

// Relative slowdown that gets called out rather than merely tabulated. The job
// informs, it does not block, so this only decides what the summary highlights.
const REGRESSION_PCT = Number(process.env.STRESS_TEST_REGRESSION_PCT ?? '25') || 25;
// Below this, a percentage is arithmetic on noise. Repeated steps report a mean
// over a ~10s window so they clear it easily; anything that does not is not
// worth flagging on.
const MIN_COMPARABLE_MS = Number(process.env.STRESS_TEST_REGRESSION_FLOOR_MS ?? '5') || 5;

const readJson = (p) => {
  try {
    return JSON.parse(readFileSync(p, 'utf8'));
  } catch {
    return undefined;
  }
};

const current = readJson(currentPath);
if (!current || !Array.isArray(current.measurements)) {
  console.log('_No stats.json for this run — nothing to compare._');
  process.exit(0);
}

const manifest = readJson(manifestPath) ?? [];
const baselines = [];
for (const entry of manifest) {
  const statsPath = join(entry.dir, 'stats.json');
  if (!existsSync(statsPath)) continue;
  const stats = readJson(statsPath);
  if (!stats || !Array.isArray(stats.measurements)) continue;
  baselines.push({
    ...entry,
    stats,
    byTitle: new Map(stats.measurements.map((m) => [m.title, m])),
    // Absent on artifacts written before the harness carried its own identity;
    // those predate repeated measurement, so they are version 1 by definition.
    harnessVersion: stats.harnessVersion ?? 1,
    stepFingerprint: stats.stepFingerprint ?? null,
  });
}

if (baselines.length === 0) {
  console.log('');
  console.log('### Version comparison');
  console.log('');
  console.log(
    '_No baseline artifacts were found for the requested tags. The first run of a tag has nothing to compare against; later runs will._'
  );
  process.exit(0);
}

const curVersion = current.harnessVersion ?? 1;
const curFingerprint = current.stepFingerprint ?? null;

const comparable = (b) =>
  b.harnessVersion === curVersion && (!curFingerprint || b.stepFingerprint === curFingerprint);

/**
 * One-cell summary of how healthy the run that produced these numbers was.
 * Failed runs are usable step by step; this says at a glance how much of the
 * column to trust before reading any single delta out of it.
 */
const health = (measurements) => {
  const failed = measurements.filter((m) => m.failed || m.timedOut).length;
  const tainted = measurements.filter((m) => m.afterFailure).length;
  if (failed === 0) return '✅ clean';
  return `⚠️ ${failed} failed${tainted ? `, ${tainted} tainted` : ''}`;
};

const pct = (cur, prev) => ((cur - prev) / prev) * 100;
const fmtPct = (v) => `${v >= 0 ? '+' : ''}${v.toFixed(1)}%`;

const out = [];
out.push('');
out.push('### Version comparison');
out.push('');
out.push(
  'Each column is the newest recorded run of that image tag, in the same migration mode. ' +
    '±% is **this run relative to that one** — positive means this run was slower. ' +
    'This section informs; it does not gate the release.'
);
out.push('');

// --- baseline legend -------------------------------------------------------
out.push(`| Column | Image tag | Recorded | Branch | Harness | Health | Comparable |`);
out.push(`|--------|-----------|----------|--------|---------|--------|------------|`);
out.push(
  `| **This run** | \`${current.imageTag ?? process.env.STRESS_TEST_IMAGE_TAG ?? '—'}\` | now | ${
    process.env.GITHUB_REF_NAME ?? '—'
  } | v${curVersion} / \`${curFingerprint ?? '—'}\` | ${health(current.measurements)} | — |`
);
for (const b of baselines) {
  const why = [];
  if (b.harnessVersion !== curVersion) why.push('harness version differs');
  if (curFingerprint && b.stepFingerprint !== curFingerprint) why.push('step set differs');
  out.push(
    `| ${b.label} | \`${b.stats.imageTag ?? b.tag}\` | ${(b.createdAt ?? '').slice(0, 10) || '—'} | ${
      b.branch || '—'
    } | v${b.harnessVersion} / \`${b.stepFingerprint ?? '—'}\` | ${health(
      b.stats.measurements
    )} | ${why.length === 0 ? '✅ yes' : `⚠️ ${why.join(', ')}`} |`
  );
}
out.push('');
if (baselines.some((b) => !comparable(b))) {
  out.push(
    '> ⚠️ A baseline marked not comparable was produced by a different measurement harness — ' +
      'its deltas include the harness change, not just the core change. Re-run that tag to refresh it.'
  );
  out.push('');
}

// --- the table -------------------------------------------------------------
const header = ['Test', 'This run', ...baselines.map((b) => `${b.label}`)];
out.push(`| ${header.join(' | ')} |`);
out.push(`|${header.map(() => '------').join('|')}|`);

const regressions = [];
for (const m of current.measurements) {
  const cells = [m.title, m.iterations ? `${m.formatted} (n=${m.iterations})` : m.formatted];
  for (const b of baselines) {
    const prev = b.byTitle.get(m.title);
    if (!prev || typeof prev.ms !== 'number' || typeof m.ms !== 'number') {
      cells.push('—');
      continue;
    }
    if (prev.failed || prev.timedOut) {
      // No usable value: a failed step's recorded time is its budget, and a
      // timed-out step's true duration is unknown by definition.
      cells.push(prev.timedOut ? '⏱️ timed out' : '❌ failed');
      continue;
    }
    const delta = pct(m.ms, prev.ms);
    // Only a clean, comparable observation on BOTH sides may raise a flag. A
    // step that ran after an earlier failure — on either side — may have been
    // slowed by that failure rather than by the code, so it is tabulated and
    // left unflagged rather than reported as a regression nobody can act on.
    const flag =
      comparable(b) &&
      !prev.afterFailure &&
      !m.afterFailure &&
      prev.ms >= MIN_COMPARABLE_MS &&
      delta >= REGRESSION_PCT
        ? ' 🔻'
        : '';
    const taint = prev.afterFailure ? ' ⚑' : '';
    cells.push(`${prev.formatted ?? `${Math.round(prev.ms)}ms`} (${fmtPct(delta)})${taint}${flag}`);
    if (flag) regressions.push({ title: m.title, label: b.label, delta, prev, cur: m });
  }
  out.push(`| ${cells.join(' | ')} |`);
}
out.push('');
out.push(
  `_\`n=\` is the number of operations averaged for that step; steps without it are a single sample. ` +
    `🔻 marks ≥${REGRESSION_PCT}% slower than a comparable baseline. ` +
    `⚑ marks a baseline value recorded after an earlier failure in that run — shown, but never flagged._`
);

// --- the headline ----------------------------------------------------------
out.push('');
if (regressions.length === 0) {
  out.push(`**No step is ≥${REGRESSION_PCT}% slower than any comparable baseline.**`);
} else {
  out.push(`**${regressions.length} step/baseline pair(s) ≥${REGRESSION_PCT}% slower:**`);
  out.push('');
  for (const r of regressions.sort((a, b) => b.delta - a.delta)) {
    out.push(
      `- \`${r.title}\` — ${fmtPct(r.delta)} vs ${r.label} (${r.prev.formatted} → ${r.cur.formatted})`
    );
  }
}

console.log(out.join('\n'));
