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

export const measureTime = async <T>(title: string, fn: () => Promise<T>): Promise<T> => {
  const st = Date.now();
  const result = await fn();
  const et = Date.now();
  const timeMs = et - st;
  console.log(`    ${title} took ${formatTime(timeMs)}`);
  StatsCollector.getInstance().addMeasurement(title, timeMs);
  return result;
};
