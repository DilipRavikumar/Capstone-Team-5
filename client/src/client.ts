import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";

async function main() {
  const transport = new StdioClientTransport({
    command: "npm",
    args: ["run", "dev"],
    cwd: "../server"
  });

  const client = new Client(
    {
      name: "my-client",
      version: "1.0.0"
    }
  );

  await client.connect(transport);

  try {
    const response = await client.callTool({
      name: "getWeather",
      arguments: {
        location: "New York"
      }
    });

    console.log("Weather Response:");
    if (response.content && Array.isArray(response.content)) {
      response.content.forEach((item: any) => {
        if (item.type === "text") {
          console.log(item.text);
        }
      });
    }
  } finally {
    await transport.close();
  }
}

main().catch(console.error);
