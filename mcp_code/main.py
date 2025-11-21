import asyncio

import httpx
from fastmcp import FastMCP


def main() -> None:
    app = FastMCP(
        "main_app",
    )
    asyncio.run(setup_main_mcp_server(app))
    app.run(transport="streamable-http")


def setup_petstore_server() -> FastMCP:
    open_api_spec_petstore = httpx.get(
        "https://petstore3.swagger.io/api/v3/openapi.json",
    ).json()

    petstore_client = httpx.AsyncClient(base_url="https://petstore3.swagger.io/api/v3")

    return FastMCP.from_openapi(
        openapi_spec=open_api_spec_petstore,
        client=petstore_client,
        name="Petstore MCP Server",
    )


def setup_tenable_server() -> FastMCP:
    open_api_spec_tenable = httpx.get(
        "https://developer.tenable.com/openapi/5c926ae6a9b73900ee2740cb",
    ).json()

    tenable_client = httpx.AsyncClient(
        base_url="https://www.tenable.com/downloads/api/v2",
    )

    return FastMCP.from_openapi(
        openapi_spec=open_api_spec_tenable,
        client=tenable_client,
        name="Tenable MCP Server",
    )


async def setup_main_mcp_server(app: FastMCP) -> None:
    petstore_server = setup_petstore_server()
    tenable_server = setup_tenable_server()                                                                                                                                                                                                                         

    await app.import_server(petstore_server)
    await app.import_server(tenable_server)


if __name__ == "__main__":
    main()
