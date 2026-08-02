import * as fs from 'fs';

export const LICENSE_FOR_TEST =
  'E1yITHflaFS4BPm7n0bnfFCjP4sJoTERmP0J=kXQ5YONtALeGnfOOe2rf2QZ0mfOh0aO3pBqfF-S0jb0ABpat6pySluTpJO6jieD6tzUOR1HrGjJO=50Ob3mHi21tQH1';

export const createStInstanceForTest = async () => {
  return {
    deployment_id: '1234567890',
    core_url: 'http://localhost:3567',
    api_key: 'qwertyuiopasdfghjklzxcvbnm',
  };
};

export const deleteStInstance = async (deploymentId: string) => {
  // noop
};

export const formatTime = (ms: number): string => {
  const seconds = Math.floor(ms / 1000);
  if (seconds < 60) {
    return `${seconds}s`;
  }
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  return `${minutes}m ${remainingSeconds}s`;
};

export const workInBatches = async <T>(
  count: number,
  numberOfBatches: number,
  work: (idx: number) => Promise<T>
): Promise<T[]> => {
  const batchSize = Math.ceil(count / numberOfBatches);
  const batches = [];
  let workCount = 0;

  const st = Date.now();
  let done = numberOfBatches;

  for (let b = 0; b < numberOfBatches; b++) {
    batches.push(
      (async () => {
        const startIndex = b * batchSize;
        const endIndex = Math.min(startIndex + batchSize, count);
        const batchResults: T[] = [];
        for (let i = startIndex; i < endIndex; i++) {
          batchResults.push(await work(i));
          workCount++;
        }
        done--;
        return batchResults;
      })()
    );
  }

  batches.push(
    (async () => {
      while (done > 0) {
        await new Promise((resolve) => setTimeout(resolve, 5000));
        const en = Date.now();
        console.log(
          `        Progress: Time=${formatTime(en - st)}, Completed=${workCount}, Throughput=${Math.round((workCount / (en - st)) * 10000) / 10}/s`
        );
      }
      return [];
    })()
  );

  const results = await Promise.all(batches);
  return results.flat();
};

export const setupLicense = async (coreUrl: string, apiKey: string) => {
  try {
    const response = await fetch(`${coreUrl}/ee/license`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'api-key': apiKey,
      },
      body: JSON.stringify({
        licenseKey: LICENSE_FOR_TEST,
      }),
    });

    if (!response.ok) {
      throw new Error(`Failed with status: ${response.status}`);
    }
    const responseText = await response.text();
    console.log('License response:', responseText);

    console.log('License key set successfully');
  } catch (error) {
    console.error('Failed to set license key:', error);
    throw error;
  }
};

export class StatsCollector {
  private static instance: StatsCollector;
  private measurements: { title: string; timeMs: number }[] = [];

  private constructor() {}

  public static getInstance(): StatsCollector {
    if (!StatsCollector.instance) {
      StatsCollector.instance = new StatsCollector();
    }
    return StatsCollector.instance;
  }

  public addMeasurement(title: string, timeMs: number) {
    this.measurements.push({ title, timeMs });
  }

  public getStats() {
    return this.measurements;
  }

  public writeToFile() {
    const formattedMeasurements = this.measurements.map((measurement) => ({
      title: measurement.title,
      ms: measurement.timeMs,
      formatted: formatTime(measurement.timeMs),
    }));

    const stats = {
      measurements: formattedMeasurements,
      timestamp: new Date().toISOString(),
    };
    fs.writeFileSync('stats.json', JSON.stringify(stats, null, 2));
  }
}

/**
 * Tracks non-OK results produced by the seeding steps. A seeding step that
 * silently errors would otherwise still "pass" and invalidate every
 * measurement downstream of it, so we count the non-OK results per step and
 * fail the run at the end if any step had failures.
 */
export class FailureTracker {
  private static instance: FailureTracker;
  private failures: Map<string, { count: number; statuses: Record<string, number> }> = new Map();

  private constructor() {}

  public static getInstance(): FailureTracker {
    if (!FailureTracker.instance) {
      FailureTracker.instance = new FailureTracker();
    }
    return FailureTracker.instance;
  }

  public recordFailure(step: string, status: string) {
    const entry = this.failures.get(step) ?? { count: 0, statuses: {} };
    entry.count++;
    entry.statuses[status] = (entry.statuses[status] ?? 0) + 1;
    this.failures.set(step, entry);
  }

  public hasFailures(): boolean {
    return this.failures.size > 0;
  }

  /**
   * Prints a per-step summary of any non-OK results and throws if there were
   * any, so the run fails at the end without aborting the seeding mid-way.
   */
  public throwIfAnyFailures() {
    if (!this.hasFailures()) {
      console.log('\nAll seeding steps completed with no non-OK results.');
      return;
    }
    console.error('\nSeeding step failures detected (non-OK results):');
    let total = 0;
    for (const [step, info] of this.failures) {
      total += info.count;
      console.error(`  ${step}: ${info.count} non-OK result(s) — ${JSON.stringify(info.statuses)}`);
    }
    throw new Error(
      `${total} non-OK result(s) across ${this.failures.size} seeding step(s); measurements are not trustworthy.`
    );
  }
}

export const measureTime = async <T>(title: string, fn: () => Promise<T>): Promise<T> => {
  const st = Date.now();
  const result = await fn();
  const et = Date.now();
  const timeMs = et - st;
  console.log(`    ${title} took ${formatTime(timeMs)}`);
  StatsCollector.getInstance().addMeasurement(title, timeMs);
  return result;
};
