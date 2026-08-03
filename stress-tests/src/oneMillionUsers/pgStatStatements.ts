import { Client } from 'pg';
import * as fs from 'fs';

// ---------------------------------------------------------------------------
// pg_stat_statements harvesting.
//
// The stress-test Postgres preloads pg_stat_statements with track=all (see
// docker-compose.yml) — this reads it so the most useful diagnostic data of
// the run isn't discarded. We snapshot twice: once when seeding finishes (then
// reset), and once at the end of the run. That separates the ingest profile
// from the steady-state read/query profile. For each phase we surface the top
// statements by total execution time and every statement that spilled to temp,
// and we fail the run if a single read-phase statement spills more than a
// configurable number of temp blocks (hardware-independent, so it catches
// hash/sort-spill regressions deterministically even on noisy runners).
// ---------------------------------------------------------------------------

export type Phase = 'seed' | 'read';

// Connection URI to the stress-test Postgres. In CI the compose publishes the
// db service's 5432 onto the runner's localhost, so the default works without
// configuration; override when the db is reachable elsewhere.
const PG_CONNECTION_URI =
  process.env.STRESS_TEST_PG_CONNECTION_URI ??
  'postgresql://supertokens:supertokens@localhost:5432/supertokens';

// A single read-phase statement that writes more than this many temp blocks
// (8 KB each, so the default ~= 80 MB) fails the run. Env-overridable, like the
// duration budgets; set STRESS_TEST_ENFORCE_TEMP_SPILL=false to report the
// spills without failing.
export const DEFAULT_TEMP_BLKS_THRESHOLD = 10_000;

const tempBlksThreshold = (): number =>
  Number(process.env.STRESS_TEST_TEMP_BLKS_THRESHOLD ?? String(DEFAULT_TEMP_BLKS_THRESHOLD)) ||
  DEFAULT_TEMP_BLKS_THRESHOLD;

const tempSpillEnforced = (): boolean =>
  (process.env.STRESS_TEST_ENFORCE_TEMP_SPILL ?? 'true').toLowerCase() !== 'false';

// How many statements to show in the "top by total_exec_time" table per phase.
const topN = (): number => Number(process.env.STRESS_TEST_PG_TOP_N ?? '15') || 15;

// Length to truncate normalized query text to in the tables.
const QUERY_TRUNCATE = 200;

export interface StatementRow {
  query: string;
  calls: number;
  totalExecTimeMs: number;
  meanExecTimeMs: number;
  rows: number;
  tempBlksWritten: number;
}

export interface PhaseStats {
  phase: Phase;
  topByTotalExecTime: StatementRow[];
  tempSpills: StatementRow[];
}

const collapse = (s: string): string => s.replace(/\s+/g, ' ').trim();

const truncate = (s: string, n = QUERY_TRUNCATE): string =>
  s.length > n ? `${s.slice(0, n - 1)}…` : s;

// Escape characters that would break a Markdown table cell.
const mdCell = (s: string): string => s.replace(/\\/g, '\\\\').replace(/\|/g, '\\|');

const fmtMs = (ms: number): string =>
  ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${ms.toFixed(1)}ms`;

const fmtInt = (n: number): string => Math.round(n).toLocaleString('en-US');

class PgStatsCollector {
  private static instance: PgStatsCollector;
  private phases: PhaseStats[] = [];

  public static getInstance(): PgStatsCollector {
    if (!PgStatsCollector.instance) {
      PgStatsCollector.instance = new PgStatsCollector();
    }
    return PgStatsCollector.instance;
  }

  public add(stats: PhaseStats) {
    this.phases.push(stats);
  }

  public getPhases(): PhaseStats[] {
    return this.phases;
  }

  private phase(phase: Phase): PhaseStats | undefined {
    return this.phases.find((p) => p.phase === phase);
  }

  /** Structured payload merged into stats.json alongside the duration measurements. */
  public toJSON() {
    return {
      tempBlksThreshold: tempBlksThreshold(),
      phases: this.phases,
    };
  }

  private static renderTable(
    heading: string,
    rows: StatementRow[],
    opts: { threshold?: number } = {}
  ): string {
    const lines: string[] = [`#### ${heading}`, ''];
    if (rows.length === 0) {
      lines.push('_none_', '');
      return lines.join('\n');
    }
    const showTemp = rows.some((r) => r.tempBlksWritten > 0) || opts.threshold !== undefined;
    const header = ['Query', 'Calls', 'Total time', 'Mean time', 'Rows'];
    if (showTemp) header.push('Temp blks');
    lines.push(`| ${header.join(' | ')} |`);
    lines.push(`|${header.map(() => '---').join('|')}|`);
    for (const r of rows) {
      const cells = [
        `\`${mdCell(truncate(r.query))}\``,
        fmtInt(r.calls),
        fmtMs(r.totalExecTimeMs),
        fmtMs(r.meanExecTimeMs),
        fmtInt(r.rows),
      ];
      if (showTemp) {
        const over = opts.threshold !== undefined && r.tempBlksWritten > opts.threshold;
        cells.push(`${fmtInt(r.tempBlksWritten)}${over ? ' ⚠️' : ''}`);
      }
      lines.push(`| ${cells.join(' | ')} |`);
    }
    lines.push('');
    return lines.join('\n');
  }

  /** Markdown for the GitHub step summary, both phases, both tables each. */
  public renderMarkdown(): string {
    if (this.phases.length === 0) return '';
    const threshold = tempBlksThreshold();
    const out: string[] = ['## pg_stat_statements', ''];
    const labels: Record<Phase, string> = {
      seed: 'Seeding phase (ingest profile)',
      read: 'Read / query phase (steady-state profile)',
    };
    for (const phase of ['seed', 'read'] as Phase[]) {
      const p = this.phase(phase);
      if (!p) continue;
      out.push(`### ${labels[phase]}`, '');
      out.push(
        PgStatsCollector.renderTable(
          `Top ${topN()} statements by total execution time`,
          p.topByTotalExecTime
        )
      );
      out.push(
        PgStatsCollector.renderTable(
          `Statements spilling to temp${
            phase === 'read' ? ` (fail threshold: ${fmtInt(threshold)} blks)` : ''
          }`,
          p.tempSpills,
          { threshold: phase === 'read' ? threshold : undefined }
        )
      );
    }
    return out.join('\n');
  }

  public writeSummaryFile(path = 'pg-stats-summary.md') {
    const md = this.renderMarkdown();
    if (md) fs.writeFileSync(path, md + '\n');
  }

  /**
   * Fails the run if any single read-phase statement spilled more than the
   * configured temp-block threshold. Called at the very end (after stats.json
   * and the summary have been written) so the tables are visible regardless.
   * Honors STRESS_TEST_ENFORCE_TEMP_SPILL=false (report-only).
   */
  public throwIfTempSpillExceeded() {
    const read = this.phase('read');
    const threshold = tempBlksThreshold();
    if (!read) {
      console.log('\nNo read-phase pg_stat_statements snapshot; skipping temp-spill check.');
      return;
    }
    const over = read.tempSpills.filter((r) => r.tempBlksWritten > threshold);
    if (over.length === 0) {
      console.log(`\nNo read-phase statement spilled more than ${fmtInt(threshold)} temp blocks.`);
      return;
    }
    console.error(`\nRead-phase statements over the temp-spill threshold (${threshold} blks):`);
    for (const r of over) {
      console.error(`  ${fmtInt(r.tempBlksWritten)} blks: ${truncate(collapse(r.query))}`);
    }
    if (!tempSpillEnforced()) {
      console.error(
        '\nSTRESS_TEST_ENFORCE_TEMP_SPILL=false — reporting only, not failing the run.'
      );
      return;
    }
    throw new Error(
      `${over.length} read-phase statement(s) spilled more than ${threshold} temp blocks; likely a hash/sort-spill regression.`
    );
  }
}

export { PgStatsCollector };

const SELECT_STATEMENTS = `
  SELECT query,
         calls,
         total_exec_time,
         mean_exec_time,
         rows,
         temp_blks_written
  FROM pg_stat_statements
  WHERE query NOT ILIKE '%pg_stat_statements%'
`;

const toRow = (r: Record<string, unknown>): StatementRow => ({
  query: collapse(String(r.query ?? '')),
  calls: Number(r.calls ?? 0),
  totalExecTimeMs: Number(r.total_exec_time ?? 0),
  meanExecTimeMs: Number(r.mean_exec_time ?? 0),
  rows: Number(r.rows ?? 0),
  tempBlksWritten: Number(r.temp_blks_written ?? 0),
});

/**
 * Snapshot pg_stat_statements for a phase into the collector. Pass reset:true
 * after seeding so the read phase starts from a clean slate. Failures here are
 * non-fatal — the run should not die because the diagnostics were unavailable —
 * so they are logged and swallowed.
 */
export const capturePgStats = async (
  phase: Phase,
  opts: { reset?: boolean } = {}
): Promise<void> => {
  const client = new Client({ connectionString: PG_CONNECTION_URI });
  try {
    await client.connect();
    // The library is preloaded, but the view needs the extension created in the
    // db. Idempotent, and it makes the harvesting self-contained.
    await client.query('CREATE EXTENSION IF NOT EXISTS pg_stat_statements');
    const res = await client.query(SELECT_STATEMENTS);
    const rows = res.rows.map(toRow);
    const topByTotalExecTime = [...rows]
      .sort((a, b) => b.totalExecTimeMs - a.totalExecTimeMs)
      .slice(0, topN());
    const tempSpills = rows
      .filter((r) => r.tempBlksWritten > 0)
      .sort((a, b) => b.tempBlksWritten - a.tempBlksWritten);
    PgStatsCollector.getInstance().add({ phase, topByTotalExecTime, tempSpills });
    console.log(
      `    pg_stat_statements (${phase}): ${rows.length} statements, ${tempSpills.length} with temp spill`
    );
    if (opts.reset) {
      await client.query('SELECT pg_stat_statements_reset()');
      console.log('    pg_stat_statements reset for the read phase');
    }
  } catch (e) {
    console.warn(`    Could not capture pg_stat_statements (${phase}): ${(e as Error).message}`);
  } finally {
    await client.end().catch(() => {});
  }
};
