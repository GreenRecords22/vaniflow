const express = require('express');
const cors = require('cors');
require('dotenv').config();

const app = express();
const PORT = process.env.PORT || 8080;
const EXPECTED_APP_ID = process.env.VANIFLOW_APP_ID || 'com.vaniflow.app';

function cleanKey(val) {
    if (!val) return '';
    return val.trim().replace(/^["']|["']$/g, '');
}

const RAW_GROQ = cleanKey(process.env.GROQ_API_KEY);
const RAW_GEMINI = cleanKey(process.env.GEMINI_API_KEY);
const RAW_OPENAI = cleanKey(process.env.OPENAI_API_KEY);

// Intelligent key type detection
const GROQ_API_KEY = RAW_GROQ.startsWith('gsk_') ? RAW_GROQ : (RAW_GROQ && !RAW_GROQ.startsWith('AIza') && !RAW_GROQ.startsWith('sk-') ? RAW_GROQ : '');
const GEMINI_API_KEY = RAW_GEMINI || (RAW_GROQ.startsWith('AIza') ? RAW_GROQ : '');
const OPENAI_API_KEY = RAW_OPENAI || (RAW_GROQ.startsWith('sk-') ? RAW_GROQ : '');

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
    let activeProvider = 'none_configured';
    if (GROQ_API_KEY) activeProvider = 'groq';
    else if (GEMINI_API_KEY) activeProvider = 'gemini';
    else if (OPENAI_API_KEY) activeProvider = 'openai';

    res.json({
        status: 'ok',
        service: 'vaniflow-ai-gateway',
        version: '1.0.0',
        primaryProvider: activeProvider,
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

    // Security 2: Client Verification
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

    // 1. Groq Provider
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
                error: { code: 'gateway_error', message: `Failed to communicate with Groq: ${err.message}` }
            });
        }
    }

    // 2. Google Gemini Provider
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
                error: { code: 'gateway_error', message: `Failed to communicate with Gemini: ${err.message}` }
            });
        }
    }

    // 3. OpenAI / OpenRouter Provider
    if (OPENAI_API_KEY) {
        try {
            const openAiMessages = [];
            if (systemPrompt && typeof systemPrompt === 'string') {
                openAiMessages.push({ role: 'system', content: systemPrompt.trim() });
            }
            if (Array.isArray(history)) {
                for (const turn of history.slice(-6)) {
                    if (turn.content) {
                        openAiMessages.push({
                            role: turn.role === 'user' ? 'user' : 'assistant',
                            content: turn.content
                        });
                    }
                }
            }
            openAiMessages.push({ role: 'user', content: userInput.trim() });

            const response = await fetch('https://api.openai.com/v1/chat/completions', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${OPENAI_API_KEY}`
                },
                body: JSON.stringify({
                    model: 'gpt-4o-mini',
                    messages: openAiMessages,
                    temperature: 0.7,
                    max_tokens: 256
                })
            });

            if (!response.ok) {
                const errText = await response.text();
                return res.status(response.status).json({
                    error: { code: `openai_${response.status}`, message: `OpenAI error: ${errText}` }
                });
            }

            const data = await response.json();
            const content = data.choices?.[0]?.message?.content || '';
            const latencyMs = Date.now() - startTime;

            return res.json({
                text: content.trim(),
                model: 'gpt-4o-mini',
                provider: 'openai',
                latencyMs,
                tokens: data.usage?.completion_tokens || Math.ceil(content.length / 4),
                characterId,
                scenarioId
            });
        } catch (err) {
            return res.status(502).json({
                error: { code: 'gateway_error', message: `Failed to communicate with OpenAI: ${err.message}` }
            });
        }
    }

    return res.status(503).json({
        error: {
            code: 'provider_unconfigured',
            message: 'No valid cloud provider API key configured on AI Gateway. Set GROQ_API_KEY, GEMINI_API_KEY, or OPENAI_API_KEY in server/.env.'
        }
    });
});

app.listen(PORT, () => {
    let provider = 'None';
    if (GROQ_API_KEY) provider = 'Groq (llama-3.1-8b-instant)';
    else if (GEMINI_API_KEY) provider = 'Google Gemini (gemini-1.5-flash)';
    else if (OPENAI_API_KEY) provider = 'OpenAI (gpt-4o-mini)';

    console.log(`[VaniFlow AI Gateway] Running on port ${PORT}`);
    console.log(`[VaniFlow AI Gateway] Active Provider: ${provider}`);
});
