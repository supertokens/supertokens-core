export const LICENSE_FOR_TEST = "E1yITHflaFS4BPm7n0bnfFCjP4sJoTERmP0J=kXQ5YONtALeGnfOOe2rf2QZ0mfOh0aO3pBqfF-S0jb0ABpat6pySluTpJO6jieD6tzUOR1HrGjJO=50Ob3mHi21tQH1";

export const createStInstanceForTest = async () => {
  try {
    const response = await fetch('https://provision.supertokens.sattvik.me/deployments', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        docker_image_name: process.env.SUPERTOKENS_DOCKER_IMAGE || "supertokens/supertokens-postgresql"
      })
    });
    
    if (!response.ok) {
      throw new Error(`HTTP error! Status: ${response.status}`);
    }
    
    const data: any = await response.json();

    const coreUrl = data.deployment.core_url;

    let isReady = false;
    let attempts = 0;
    const maxAttempts = 30;
    const retryDelay = 2000;
    
    while (!isReady && attempts < maxAttempts) {
      try {
        const healthCheck = await fetch(`${coreUrl}/health`, {
          method: 'GET',
          headers: {
            'Accept': 'application/json'
          }
        });
        
        if (healthCheck.ok) {
          isReady = true;
        } else {
          attempts++;
          await new Promise(resolve => setTimeout(resolve, retryDelay));
        }
      } catch (err) {
        attempts++;
        await new Promise(resolve => setTimeout(resolve, retryDelay));
      }
    }
    
    if (!isReady) {
      throw new Error(`Core URL ${coreUrl} did not become ready after ${maxAttempts} attempts`);
    }

    return data.deployment!;
  } catch (error) {
    throw error;
  }

}

export const deleteStInstance = async (deploymentId: string) => {
  try {
    const response = await fetch(`https://provision.supertokens.sattvik.me/deployments/${deploymentId}`, {
      method: 'DELETE'
    });
  } finally {}
}

export const formatTime = (ms: number): string => {
  const seconds = Math.floor(ms / 1000);
  if (seconds < 60) {
    return `${seconds}s`;
  }
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  return `${minutes}m ${remainingSeconds}s`;
}

export const workInBatches = async<T>(count: number, numberOfBatches: number, work: (idx: number) => Promise<T>): Promise<T[]> => {
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

  batches.push((async () => {
    while (done > 0) {
      await new Promise(resolve => setTimeout(resolve, 5000));
      const en = Date.now();
      console.log(`        Progress: Time=${formatTime(en - st)}, Completed=${workCount}, Throughput=${Math.round(workCount / (en - st) * 10000) / 10}/s`);
    }
    return [];
  })())

  const results = await Promise.all(batches);
  return results.flat();
}

export const setupLicense = async (coreUrl: string, apiKey: string) => {
  try {
    const response = await fetch(`${coreUrl}/ee/license`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        "api-key": apiKey
      },
      body: JSON.stringify({
        licenseKey: LICENSE_FOR_TEST
      })
    });

    if (!response.ok) {
      throw new Error(`Failed with status: ${response.status}`);
    }
    const responseText = await response.text();
    console.log("License response:", responseText);

    console.log("License key set successfully");
  } catch (error) {
    console.error("Failed to set license key:", error);
    throw error;
  }
};

export const measureTime = async <T>(title: string, fn: () => Promise<T>): Promise<T> => {
  const st = Date.now();
  const result = await fn();
  const et = Date.now();
  console.log(`    ${title} took ${formatTime(et - st)}`);
  return result;
}
