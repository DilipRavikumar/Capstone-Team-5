import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { weatherToolConfig, weatherToolHandler } from "./weatherTool";

async function main() {
  const mcpServer = new McpServer({
    name: "bedrock-mcp-server",
    version: "1.0.0"
  });

  mcpServer.registerTool("getWeather", weatherToolConfig, weatherToolHandler);

  const transport = new StdioServerTransport();
  await mcpServer.connect(transport);
}

main().catch(err => console.error(err));
