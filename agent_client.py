import asyncio
from typing import Dict, Any

from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain.agents import create_agent


# 1. Build MCP client that connects to your FastMCP gateway
def build_mcp_client() -> MultiServerMCPClient:
    """
    Wrap your Gateway MCP Server as a single MCP server called 'gateway'.

    Internally, this gateway already exposes Petstore, Tenable, Canonical tools,
    so the agent will see ALL of them as tools.
    """
    return MultiServerMCPClient(
        {
            "gateway": {
                "transport": "streamable_http",         
                "url": "http://127.0.0.1:8000/mcp",      
            }
        }
    )


async def build_agent() -> Any:
    """
    Create a LangChain agent that uses all tools from the MCP gateway.
    """
    client = build_mcp_client()

    # Fetch all tools from all MCP servers (here: just 'gateway')
    tools = await client.get_tools()

    # create_agent(model_id, tools) comes from langchain.agents
    # This will internally create a ChatOpenAI model using OPENAI_API_KEY.
    # agent = create_agent("ollama:phi3", tools)
    agent = create_agent("openai:gpt-4.1", tools)

    return agent


async def chat_loop():
    """
    Very simple terminal client:
    - Reads natural language from stdin
    - Sends it to the LangChain agent
    - Agent decides which MCP tool(s) to call
    - Prints final answer
    """
    agent = await build_agent()

    print("✅ MCP LangChain agent is ready.")
    print("Type your questions (e.g. 'list available pets', 'get canonical users', etc.)")
    print("Type 'exit' or 'quit' to stop.\n")

    while True:
        user_input = input("You: ").strip()
        if user_input.lower() in {"exit", "quit"}:
            print("Bye!")
            break

        if not user_input:
            continue

        # Agent expects a dict with "messages" key (see langchain-mcp-adapters README)
        try:
            result: Dict[str, Any] = await agent.ainvoke({"messages": user_input})
        except Exception as e:
            print(f"⚠️ Error during agent invocation: {e}")
            continue

        # result["messages"] is a list of LangChain messages; last one is model reply
        messages = result.get("messages", [])
        if not messages:
            print("⚠️ No messages returned from agent.")
            continue

        final_msg = messages[-1]
        # final_msg is usually a ChatMessage / AIMessage with .content
        print(f"Agent: {getattr(final_msg, 'content', final_msg)}\n")


if __name__ == "__main__":
    asyncio.run(chat_loop())
