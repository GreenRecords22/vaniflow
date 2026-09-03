const express = require('express');
const cors = require('cors');
require('dotenv').config();

const app = express();
const PORT = process.env.PORT || 8080;
const EXPECTED_APP_ID = process.env.VANIFLOW_APP_ID || 'com.vaniflow.app';
const GROQ_API_KEY = process.env.GROQ_API_KEY || '';
const GEMINI_API_KEY = process.env.GEMINI_API_KEY || '';
const DEFAULT_MODEL = process.env.DEFAULT_MODEL || 'llama-3.1-8b-instant';

app.use(cors());
app.use(express.json({ limit: '256kb' }));

// 1. In-memory rate limiting (60 requests/minute per client IP)
const rateLimitMap = new Map();
const RATE_LIMIT_WINDOW_MS = 60 * 1000;
const MAX_REQUESTS_PER_WINDOW = 60;

function checkRateLimit(ip) {
    const now = Date.now();
    const entry = rateLimitMap.get(ip);
    if (!entry || now - entry.startTime > RATE_LIMIT_WINDOW_MS) {
        rateLimitMap.set(ip, { startTime: now, count: 1 });
        return true;
    }
    if (entry.count >= MAX_REQUESTS_PER_WINDOW) {
        return false;
    }
    entry.count += 1;
    return true;
}

// Periodic cleanup of rateLimitMap
setInterval(() => {
    const now = Date.now();
    for (const [ip, entry] of rateLimitMap.entries()) {
        if (now - entry.startTime > RATE_LIMIT_WINDOW_MS) {
            rateLimitMap.delete(ip);
        }
    }
}, RATE_LIMIT_WINDOW_MS);

// 2. Health & Status Check
app.get('/health', (req, res) => {
    res.json({
        status: 'ok',
        service: 'vaniflow-ai-gateway',
        version: '1.0.0',
        primaryProvider: GROQ_API_KEY ? 'groq' : (GEMINI_API_KEY ? 'gemini' : 'none_configured'),
        timestamp: new Date().toISOString()
    });
});

// 3. Normalized Chat Completions API
app.post('/v1/chat', async (req, res) => {
    const clientIp = req.headers['x-forwarded-for'] || req.socket.remoteAddress || '127.0.0.1';
    
    // Security 1: Rate Limiting
    if (!checkRateLimit(clientIp)) {
        return res.status(429).json({
            error: {
                code: 'rate_limit_exceeded',
                message: 'Too many requests. Please slow down.'
            }
        });
    }

    // Security 2: Client Verification (optional in local dev, enforced in production if configured)
    const appId = req.headers['x-vaniflow-app-id'];
    if (process.env.NODE_ENV === 'production' && appId !== EXPECTED_APP_ID) {
        return res.status(401).json({
            error: {
                code: 'unauthorized_client',
                message: 'Invalid or missing application identifier header.'
            }
        });
    }

    // Request Validation
    const { systemPrompt, history = [], userInput, characterId = 'raya', scenarioId = 'general', stream = false } = req.body;

    if (!userInput || typeof userInput !== 'string' || !userInput.trim()) {
        return res.status(400).json({
            error: {
                code: 'invalid_request',
                message: 'Field userInput must be a non-empty string.'
            }
        });
    }

    const startTime = Date.now();

    // Check if Groq provider is configured
    if (GROQ_API_KEY) {
        try {
            const groqMessages = [];
            if (systemPrompt && typeof systemPrompt === 'string') {
                groqMessages.push({ role: 'system', content: systemPrompt.trim() });
            }
            if (Array.isArray(history)) {
                for (const turn of history.slice(-6)) {
                    if (turn.content) {
                        groqMessages.push({
                            role: turn.role === 'user' ? 'user' : 'assistant',
                            content: turn.content
                        });
                    }
                }
            }
            groqMessages.push({ role: 'user', content: userInput.trim() });

            if (stream) {
                // Streaming response
                const response = await fetch('https://api.groq.com/openai/v1/chat/completions', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${GROQ_API_KEY}`
                    },
                    body: JSON.stringify({
                        model: DEFAULT_MODEL,
                        messages: groqMessages,
                        temperature: 0.7,
                        max_tokens: 256,
                        stream: true
                    })
                });

                if (!response.ok) {
                    const errText = await response.text();
                    return res.status(response.status).json({
                        error: { code: `groq_${response.status}`, message: `Provider error: ${errText}` }
                    });
                }

                res.setHeader('Content-Type', 'text/event-stream');
                res.setHeader('Cache-Control', 'no-cache');
                res.setHeader('Connection', 'keep-alive');

                const reader = response.body.getReader();
                const decoder = new TextDecoder();
                while (true) {
                    const { done, value } = await reader.read();
                    if (done) break;
                    res.write(decoder.decode(value, { stream: true }));
                }
                return res.end();
            } else {
                // Standard non-streaming response
                const response = await fetch('https://api.groq.com/openai/v1/chat/completions', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${GROQ_API_KEY}`
                    },
                    body: JSON.stringify({
                        model: DEFAULT_MODEL,
                        messages: groqMessages,
                        temperature: 0.7,
                        max_tokens: 256,
                        stream: false
                    })
                });

                if (!response.ok) {
                    const errText = await response.text();
                    return res.status(response.status).json({
                        error: { code: `groq_${response.status}`, message: `Provider error: ${errText}` }
                    });
                }

                const data = await response.json();
                const content = data.choices?.[0]?.message?.content || '';
                const latencyMs = Date.now() - startTime;

                return res.json({
                    text: content.trim(),
                    model: DEFAULT_MODEL,
                    provider: 'groq',
                    latencyMs,
                    tokens: data.usage?.completion_tokens || Math.ceil(content.length / 4),
                    characterId,
                    scenarioId
                });
            }
        } catch (err) {
            return res.status(502).json({
                error: {
                    code: 'gateway_error',
                    message: `Failed to communicate with provider: ${err.message}`
                }
            });
        }
    }

    // Fallback: Gemini provider if configured
    if (GEMINI_API_KEY) {
        try {
            const contents = [];
            if (Array.isArray(history)) {
                for (const turn of history.slice(-6)) {
                    contents.push({
                        role: turn.role === 'user' ? 'user' : 'model',
                        parts: [{ text: turn.content }]
                    });
                }
            }
            contents.push({
                role: 'user',
                parts: [{ text: userInput.trim() }]
            });

            const body = {
                contents,
                generationConfig: {
                    temperature: 0.7,
                    maxOutputTokens: 256
                }
            };
            if (systemPrompt) {
                body.systemInstruction = {
                    parts: [{ text: systemPrompt.trim() }]
                };
            }

            const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${GEMINI_API_KEY}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });

            if (!response.ok) {
                const errText = await response.text();
                return res.status(response.status).json({
                    error: { code: `gemini_${response.status}`, message: `Gemini error: ${errText}` }
                });
            }

            const data = await response.json();
            const text = data.candidates?.[0]?.content?.parts?.[0]?.text || '';
            const latencyMs = Date.now() - startTime;

            return res.json({
                text: text.trim(),
                model: 'gemini-1.5-flash',
                provider: 'gemini',
                latencyMs,
                tokens: Math.ceil(text.length / 4),
                characterId,
                scenarioId
            });
        } catch (err) {
            return res.status(502).json({
                error: {
                    code: 'gateway_error',
                    message: `Failed to communicate with Gemini: ${err.message}`
                }
            });
        }
    }

    // If no provider credential is set on the server
    return res.status(503).json({
        error: {
            code: 'provider_unconfigured',
            message: 'No cloud provider API key configured on AI Gateway. Please set GROQ_API_KEY or GEMINI_API_KEY in server environment.'
        }
    });
});

app.listen(PORT, () => {
    console.log(`[VaniFlow AI Gateway] Running on port ${PORT}`);
    console.log(`[VaniFlow AI Gateway] Primary Provider: ${GROQ_API_KEY ? 'Groq (llama-3.1-8b-instant)' : (GEMINI_API_KEY ? 'Gemini 1.5 Flash' : 'None (Set GROQ_API_KEY)')}`);
});
