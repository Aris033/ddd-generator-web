import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

type Example = {
  name: string;
  structure: string;
};

type GeneratorRequest = {
  project: string;
  groupId: string;
  artifactId: string;
  persistence: string;
  examples: Example[];
};

type PreviewResponse = {
  project: string;
  folders: string[];
  entities: string[];
};

type FieldDraft = {
  name: string;
  type: string;
};

const supportedTypes = ['String', 'Integer', 'Long', 'Boolean', 'BigDecimal', 'UUID', 'Instant', 'LocalDate'];

const blankRequest: GeneratorRequest = {
  project: 'DemoHexProject',
  groupId: 'com.ignacio.demo',
  artifactId: '',
  persistence: 'h2',
  examples: [
    { name: 'User', structure: 'name:String,email:String,age:Integer,birthDate:LocalDate' },
    { name: 'Order', structure: 'code:String,total:BigDecimal,paid:Boolean,createdOn:LocalDate' },
  ],
};

function App() {
  const [form, setForm] = useState<GeneratorRequest>(blankRequest);
  const [preview, setPreview] = useState<PreviewResponse | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    void loadDefaults();
  }, []);

  const entityCount = useMemo(() => form.examples.filter((item) => item.name.trim()).length, [form.examples]);
  const fieldCount = useMemo(
    () => form.examples.reduce((total, example) => total + parseFields(example.structure).length, 0),
    [form.examples],
  );

  async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
    const response = await fetch(url, init);
    if (!response.ok) {
      let message = `Request failed with ${response.status}`;
      try {
        const body = await response.json();
        if (Array.isArray(body.errors)) {
          message = body.errors.join(' ');
        } else if (body.message) {
          message = body.message;
        }
      } catch {
        // Keep the status-based message.
      }
      throw new Error(message);
    }
    return response.json() as Promise<T>;
  }

  async function loadDefaults() {
    setBusy(true);
    setError('');
    try {
      const defaults = await requestJson<GeneratorRequest>('/api/defaults');
      setForm(defaults);
      setPreview(null);
    } catch (exception) {
      setError(messageFrom(exception));
    } finally {
      setBusy(false);
    }
  }

  async function previewStructure() {
    setBusy(true);
    setError('');
    try {
      const result = await requestJson<PreviewResponse>('/api/preview', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(form),
      });
      setPreview(result);
    } catch (exception) {
      setError(messageFrom(exception));
    } finally {
      setBusy(false);
    }
  }

  async function generateZip() {
    setBusy(true);
    setError('');
    try {
      const response = await fetch('/api/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(form),
      });
      if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new Error(Array.isArray(body?.errors) ? body.errors.join(' ') : 'Could not generate ZIP.');
      }
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${artifactName(form)}.zip`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (exception) {
      setError(messageFrom(exception));
    } finally {
      setBusy(false);
    }
  }

  function updateExample(index: number, patch: Partial<Example>) {
    setForm((current) => ({
      ...current,
      examples: current.examples.map((example, itemIndex) =>
        itemIndex === index ? { ...example, ...patch } : example,
      ),
    }));
  }

  function removeExample(index: number) {
    setForm((current) => ({
      ...current,
      examples: current.examples.filter((_, itemIndex) => itemIndex !== index),
    }));
  }

  function addExample() {
    setForm((current) => ({
      ...current,
      examples: [...current.examples, { name: 'Invoice', structure: 'number:String,total:BigDecimal' }],
    }));
  }

  function updateField(exampleIndex: number, fieldIndex: number, patch: Partial<FieldDraft>) {
    const fields = parseFields(form.examples[exampleIndex].structure);
    const updated = fields.map((field, index) => (index === fieldIndex ? { ...field, ...patch } : field));
    updateExample(exampleIndex, { structure: stringifyFields(updated) });
  }

  function addField(exampleIndex: number) {
    const fields = parseFields(form.examples[exampleIndex].structure);
    updateExample(exampleIndex, { structure: stringifyFields([...fields, { name: 'value', type: 'String' }]) });
  }

  function removeField(exampleIndex: number, fieldIndex: number) {
    const fields = parseFields(form.examples[exampleIndex].structure);
    const updated = fields.filter((_, index) => index !== fieldIndex);
    updateExample(exampleIndex, { structure: stringifyFields(updated.length ? updated : [{ name: 'value', type: 'String' }]) });
  }

  return (
    <main className="app-shell">
      <section className="intro">
        <div>
          <p className="eyebrow">Spring Boot + React + Docker</p>
          <h1>DDD / Hexagonal Project Generator</h1>
          <p>
            Design your domain model and download a ready-to-run Spring Boot scaffold with domain, application and
            infrastructure layers.
          </p>
        </div>
        <div className="metrics-grid" aria-label="Current form summary">
          <div className="metrics">
            <span>{entityCount}</span>
            <small>entities</small>
          </div>
          <div className="metrics accent">
            <span>{fieldCount}</span>
            <small>fields</small>
          </div>
        </div>
      </section>

      {error && <div className="alert">{error}</div>}

      <section className="workspace">
        <form className="panel form-panel" onSubmit={(event) => event.preventDefault()}>
          <div className="panel-heading">
            <div>
              <h2>Project setup</h2>
              <p>Choose the Maven coordinates and persistence mode.</p>
            </div>
            <button type="button" className="secondary" onClick={loadDefaults} disabled={busy}>
              Load default example
            </button>
          </div>

          <label>
            Project name
            <input value={form.project} onChange={(event) => setForm({ ...form, project: event.target.value })} />
          </label>

          <div className="field-grid">
            <label>
              Group ID
              <input value={form.groupId} onChange={(event) => setForm({ ...form, groupId: event.target.value })} />
            </label>
            <label>
              Artifact ID
              <input
                value={form.artifactId}
                placeholder="auto-generated when empty"
                onChange={(event) => setForm({ ...form, artifactId: event.target.value })}
              />
            </label>
          </div>

          <label>
            Persistence
            <select value={form.persistence} onChange={(event) => setForm({ ...form, persistence: event.target.value })}>
              <option value="h2">h2</option>
              <option value="memory">memory</option>
            </select>
          </label>

          <div className="entities-heading">
            <div>
              <h2>Entities</h2>
              <p>Model each aggregate with typed fields.</p>
            </div>
            <button type="button" className="secondary" onClick={addExample} disabled={busy || form.examples.length >= 10}>
              Add entity
            </button>
          </div>

          <div className="entities">
            {form.examples.map((example, index) => (
              <div className="entity-card" key={`${example.name}-${index}`}>
                <div className="entity-topline">
                  <label>
                    Entity name
                    <input value={example.name} onChange={(event) => updateExample(index, { name: event.target.value })} />
                  </label>
                  <button
                    type="button"
                    className="ghost-danger"
                    aria-label={`Remove ${example.name || 'entity'}`}
                    onClick={() => removeExample(index)}
                    disabled={busy || form.examples.length === 1}
                  >
                    Remove
                  </button>
                </div>

                <div className="field-editor">
                  <div className="field-editor-head">
                    <span>Fields</span>
                    <button type="button" className="mini-button" onClick={() => addField(index)} disabled={busy}>
                      Add field
                    </button>
                  </div>
                  {parseFields(example.structure).map((field, fieldIndex) => (
                    <div className="field-row" key={`${field.name}-${fieldIndex}`}>
                      <label>
                        Name
                        <input
                          value={field.name}
                          placeholder="email"
                          onChange={(event) => updateField(index, fieldIndex, { name: event.target.value })}
                        />
                      </label>
                      <label>
                        Type
                        <select
                          value={supportedTypes.includes(field.type) ? field.type : 'String'}
                          onChange={(event) => updateField(index, fieldIndex, { type: event.target.value })}
                        >
                          {supportedTypes.map((type) => (
                            <option value={type} key={type}>
                              {type}
                            </option>
                          ))}
                        </select>
                      </label>
                      <button
                        type="button"
                        className="icon-button"
                        aria-label={`Remove ${field.name || 'field'}`}
                        onClick={() => removeField(index, fieldIndex)}
                        disabled={busy || parseFields(example.structure).length === 1}
                      >
                        x
                      </button>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>

          <div className="actions">
            <button type="button" className="secondary" onClick={previewStructure} disabled={busy}>
              Preview structure
            </button>
            <button type="button" className="primary" onClick={generateZip} disabled={busy}>
              Generate ZIP
            </button>
          </div>
        </form>

        <aside className="panel preview-panel">
          <div className="panel-heading">
            <div>
              <h2>Preview</h2>
              <p>Generated package shape.</p>
            </div>
            <span>{preview?.project ?? form.project}</span>
          </div>
          {preview ? (
            <>
              <div className="preview-block">
                <h3>Folders</h3>
                <ul>
                  {preview.folders.map((folder) => (
                    <li key={folder}>{folder}</li>
                  ))}
                </ul>
              </div>
              <div className="preview-block">
                <h3>Entities</h3>
                <div className="chips">
                  {preview.entities.map((entity) => (
                    <span key={entity}>{entity}</span>
                  ))}
                </div>
              </div>
            </>
          ) : (
            <div className="empty-preview">Run a preview to inspect the generated layers.</div>
          )}
        </aside>
      </section>
    </main>
  );
}

function artifactName(form: GeneratorRequest) {
  return (form.artifactId || form.project)
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '');
}

function parseFields(structure: string): FieldDraft[] {
  const fields = structure
    .split(',')
    .map((token) => token.trim())
    .filter(Boolean)
    .map((token) => {
      const [name = '', type = 'String'] = token.split(':');
      return {
        name: name.trim(),
        type: supportedTypes.includes(type.trim()) ? type.trim() : 'String',
      };
    });
  return fields.length ? fields : [{ name: 'value', type: 'String' }];
}

function stringifyFields(fields: FieldDraft[]) {
  return fields.map((field) => `${field.name.trim()}:${field.type}`).join(',');
}

function messageFrom(exception: unknown) {
  return exception instanceof Error ? exception.message : 'Unexpected error.';
}

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
