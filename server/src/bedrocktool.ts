import { BedrockRuntimeClient, InvokeModelCommand } from "@aws-sdk/client-bedrock-runtime";
import dotenv from "dotenv";
import { z } from "zod";

dotenv.config();

const client = new BedrockRuntimeClient({
  region: process.env.AWS_REGION,
  credentials: {
    accessKeyId: process.env.AWS_ACCESS_KEY_ID!,
    secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY!,
    sessionToken: process.env.AWS_SESSION_TOKEN
  }
});

export const bedrockToolConfig = {
  description: "Send a text input to AWS Bedrock and get a model response.",
  inputSchema: {
    prompt: z.string().describe("The text prompt to send to Bedrock")
  }
};

export async function bedrockToolHandler({ prompt }: { prompt: string }) {
  try {
    const modelId = process.env.BEDROCK_MODEL_ID!;
    let body: any;
    const handlerState: any = {};

    if (modelId.startsWith("anthropic")) {
      body = JSON.stringify({
        messages: [
          {
            role: "user",
            content: [{ text: prompt }]
          }
        ],
        max_tokens: 150
      });
    }

  
    else if (modelId.includes("amazon.nova")) {
      const candidatePayloads = [
        { input: { messages: [{ role: "user", content: [{ text: prompt }] }] } },
        { messages: [{ role: "user", content: [{ text: prompt }] }] }
      ];

      let lastErr: any = null;
      for (const payload of candidatePayloads) {
        try {
          const cmdTry = new InvokeModelCommand({ modelId, body: JSON.stringify(payload) });
          const respTry = await client.send(cmdTry);
          const jsonTry = JSON.parse(new TextDecoder().decode(respTry.body));
        
          if (jsonTry && (jsonTry.output_text || jsonTry.content || jsonTry.results || jsonTry.completions)) {
            handlerState.json = jsonTry;
            lastErr = null;
            break;
          }
        } catch (e) {
          lastErr = e;
        }
      }

      if (lastErr) {
        // If all attempts failed, throw the last error to be caught by outer try
        throw lastErr;
      }
    }

    //  Meta Llama 3
    else if (modelId.startsWith("meta.llama3")) {
      body = JSON.stringify({
        prompt: prompt,
        max_gen_len: 150
      });
    }

    // Default fallback 
    else {
      body = JSON.stringify({
        inputText: prompt,
        textGenerationConfig: {
          maxTokenCount: 150
        }
      });
    }

    let json: any;
    if (handlerState.json) {
      json = handlerState.json;
    } else {
      const cmd = new InvokeModelCommand({ modelId, body });
      const response = await client.send(cmd);
      json = JSON.parse(new TextDecoder().decode(response.body));
    }

    let text =
      json.output_text ||
      json.content?.[0]?.text ||
      json.results?.[0]?.outputText ||
      json.completions?.[0]?.data?.text ||
      JSON.stringify(json);

    return {
      content: [
        {
          type: "text" as const,
          text
        }
      ]
    };

  } catch (err: any) {
    return {
      content: [
        {
          type: "text" as const,
          text: `Error: ${err.message}`
        }
      ]
    };
  }
}

