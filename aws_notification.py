import asyncio
import os
from typing import Any, Dict

from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain_aws import ChatBedrock
from langchain_classic.agents import create_tool_calling_agent, AgentExecutor
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder

from notification import send_notification   


def build_mcp_client() -> MultiServerMCPClient:
    return MultiServerMCPClient(
        {
            "gateway": {
                "transport": "streamable_http",
                "url": "http://127.0.0.1:8000/mcp",
            }
        }
    )


async def build_agent() -> AgentExecutor:
    client = build_mcp_client()
    tools = await client.get_tools()

    llm = ChatBedrock(
        model_id="us.amazon.nova-pro-v1:0",
        region_name=os.environ.get("AWS_REGION", "us-east-2"),
    )

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

    agent_runnable = create_tool_calling_agent(
        llm=llm,
        tools=tools,
        prompt=prompt,
    )

    agent_executor = AgentExecutor(
        agent=agent_runnable,
        tools=tools,
        verbose=True,
    )
    return agent_executor


async def chat_loop():
    agent = await build_agent()

    print("MCP + Amazon Nova Pro agent is ready.")
    print("Type 'exit' or 'quit' to stop.\n")

    while True:
        user_input = input("You: ").strip()
        if user_input.lower() in {"exit", "quit"}:
            print("Bye!")
            break

        if not user_input:
            continue

        try:
            result: Dict[str, Any] = await agent.ainvoke({"input": user_input})
        except Exception as e:
            print(f" Error during agent invocation: {e}")
            continue

        output = result.get("output", "")
        print(f"Agent: {output}\n")

        #  Simple example rule: notify when user asks about "critical" or "high severity"
        if any(word in user_input.lower() for word in ["critical", "high severity", "alert"]):
            subject = "MCP Agent Alert"
            message = f"User query: {user_input}\n\nAgent response:\n{output}"
            send_notification(subject, message)


if __name__ == "__main__":
    asyncio.run(chat_loop())
