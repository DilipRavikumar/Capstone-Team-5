import asyncio
import os
from typing import Any, Dict

from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain_aws import ChatBedrock 
from langchain_classic.agents import create_tool_calling_agent, AgentExecutor
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder


# 1. Connect to your MCP Gateway server
def build_mcp_client() -> MultiServerMCPClient:
    """
    Wrap your FastMCP gateway server as a single MCP server called 'gateway'.
    The FastMCP server is running at: http://127.0.0.1:8000/mcp
    and exposes Petstore, Tenable, Canonical tools.
    """
    return MultiServerMCPClient(
        {
            "gateway": {
                "transport": "streamable_http",
                "url": "http://127.0.0.1:8000/mcp",
            }
        }
    )


# 2. Build an agent that uses Amazon Nova Pro on Bedrock
async def build_agent() -> AgentExecutor:
    client = build_mcp_client()

    # Get all MCP tools (petstore, tenable, canonical, etc.)
    tools = await client.get_tools()

    # LLM: Amazon Nova Pro via Bedrock
    llm = ChatBedrock(
        model_id="us.amazon.nova-pro-v1:0",          # Nova Pro model ID
        region_name=os.environ.get("AWS_REGION", "us-east-2"),
    )

    # Prompt for the tool-calling agent
    prompt = ChatPromptTemplate.from_messages(
        [
            (
                "system",
                "You are a helpful assistant with access to multiple HTTP APIs "
                "via MCP tools (petstore, tenable, canonical). "
                "Call tools whenever they are useful to answer the question.",
            ),
            ("user", "{input}"),
            MessagesPlaceholder("agent_scratchpad"),
        ]
    )

    # Build a generic tool-calling agent
    agent_runnable = create_tool_calling_agent(
        llm=llm,
        tools=tools,
        prompt=prompt,
    )

    # Wrap in AgentExecutor for automatic tool execution loop
    agent_executor = AgentExecutor(
        agent=agent_runnable,
        tools=tools,
        verbose=True,
    )

    return agent_executor


# 3. Simple CLI loop
async def chat_loop():
    agent = await build_agent()

    print("MCP + Amazon Nova Pro agent is ready.")
    print("Type your questions (e.g. 'list available pets', 'get the pet with id 1').")
    print("Type 'exit' or 'quit' to stop.\n")

    while True:
        user_input = input("You: ").strip()
        if user_input.lower() in {"exit", "quit"}:
            print("Bye!")
            break

        if not user_input:
            continue

        try:
            # AgentExecutor expects {"input": "..."}
            result: Dict[str, Any] = await agent.ainvoke({"input": user_input})
        except Exception as e:
            print(f" Error during agent invocation: {e}")
            continue

        # AgentExecutor puts final text in result["output"]
        print(f"Agent: {result.get('output')}\n")


if __name__ == "__main__":
    asyncio.run(chat_loop())
