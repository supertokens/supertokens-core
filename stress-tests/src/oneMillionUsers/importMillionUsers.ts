import fs from 'fs';
import { formatTime, measureTime } from '../common/utils';

export const importMillionUsers = async (deployment: any) => {
    console.log("\n\n0. Importing one million users");

    // Create roles
    await fetch(`${deployment.core_url}/recipe/role`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json",
            "Api-Key": deployment.api_key
        },
        body: JSON.stringify({ 
            "role": "role1", 
            "permissions": ["p1", "p2"] 
        })
    });
    
    await fetch(`${deployment.core_url}/recipe/role`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json",
            "Api-Key": deployment.api_key
        },
        body: JSON.stringify({ 
            "role": "role2", 
            "permissions": ["p3", "p2"] 
        })
    });

    await measureTime("Loading users for bulk import", async () => {
        for (let i = 0; i < 100; i++) {
            const filename = `users/users-${i.toString().padStart(4, '0')}.json`;
            const fileData = fs.readFileSync(filename, 'utf8');
            const data = JSON.parse(fileData);
    
            await fetch(`${deployment.core_url}/bulk-import/users`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Api-Key": deployment.api_key
                },
                body: JSON.stringify(data)
            });
        }
    })

    let lastCount = 1000000;
    let st = Date.now();
    let lastTime = st;
    while (true) {
        await new Promise(resolve => setTimeout(resolve, 5000));
        const response = await fetch(`${deployment.core_url}/bulk-import/users/count`, {
            headers: {
                "Api-Key": deployment.api_key
            }
        });
        const count: any = await response.json();
        console.log(`    Progress: Time=${formatTime(Date.now() - st)}, UsersLeft=${count.count}, Rate=${((lastCount - count.count) / (Date.now() - lastTime)).toFixed(1)}`);

        if (count.count === 0) {
            break;
        }

        lastCount = count.count;
        lastTime = Date.now();
    }
}