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
  examples: [{ name: 'User', structure: 'name:String,email:String,age:Integer,birthDate:LocalDate' }],
};

const steps = ['Project', 'Entity', 'Export'];

function App() {
  const [form, setForm] = useState<GeneratorRequest>(blankRequest);
  const [preview, setPreview] = useState<PreviewResponse | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [step, setStep] = useState(0);
  const [activeEntityIndex, setActiveEntityIndex] = useState(0);
  const [newField, setNewField] = useState<FieldDraft>({ name: '', type: 'String' });

  useEffect(() => {
    void loadDefaults();
  }, []);

  const activeEntity = form.examples[activeEntityIndex] ?? form.examples[0];
  const activeFields = useMemo(() => parseFields(activeEntity?.structure ?? ''), [activeEntity]);
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
        message = Array.isArray(body.errors) ? body.errors.join(' ') : body.message || message;
      } catch {
        // Keep status message.
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
      setActiveEntityIndex(0);
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
      setStep(2);
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
    setPreview(null);
  }

  function addEntity() {
    const nextIndex = form.examples.length + 1;
    const next = { name: `Entity${nextIndex}`, structure: 'name:String' };
    setForm((current) => ({ ...current, examples: [...current.examples, next] }));
    setActiveEntityIndex(form.examples.length);
    setNewField({ name: '', type: 'String' });
    setStep(1);
    setPreview(null);
  }

  function removeEntity(index: number) {
    const examples = form.examples.filter((_, itemIndex) => itemIndex !== index);
    const safeExamples = examples.length ? examples : [{ name: 'User', structure: 'name:String' }];
    setForm((current) => ({ ...current, examples: safeExamples }));
    setActiveEntityIndex(Math.max(0, Math.min(index - 1, safeExamples.length - 1)));
    setPreview(null);
  }

  function addFieldFromComposer() {
    const cleanName = newField.name.trim();
    if (!cleanName) {
      setError('Add a field name before adding it.');
      return;
    }
    setError('');
    updateExample(activeEntityIndex, {
      structure: stringifyFields([...activeFields, { name: cleanName, type: newField.type }]),
    });
    setNewField({ name: '', type: 'String' });
  }

  function removeField(fieldIndex: number) {
    const updated = activeFields.filter((_, index) => index !== fieldIndex);
    updateExample(activeEntityIndex, {
      structure: stringifyFields(updated.length ? updated : [{ name: 'name', type: 'String' }]),
    });
  }

  function replaceField(fieldIndex: number, patch: Partial<FieldDraft>) {
    const updated = activeFields.map((field, index) => (index === fieldIndex ? { ...field, ...patch } : field));
    updateExample(activeEntityIndex, { structure: stringifyFields(updated) });
  }

  function goToBuilder() {
    document.getElementById('builder')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  return (
    <main>
      <section className="hero">
        <div className="logo-stage">
          <div className="logo-card">
            <img src="/aris033-logo.png" alt="Aris033 pixel art Java React SQL logo" />
          </div>
          <div className="hero-actions">
            <button type="button" className="primary start-button" onClick={goToBuilder}>
              Start
            </button>
            <button type="button" className="secondary" onClick={loadDefaults} disabled={busy}>
              Demo data
            </button>
          </div>
        </div>

        <div className="hero-copy">
          <p className="eyebrow">Portfolio demo</p>
          <h1>DDD / Hexagonal Project Generator</h1>
          <p className="hero-subtitle">Spring Boot, React and Docker in one downloadable scaffold.</p>
          <div className="stack-pills" aria-label="Stack">
            <span>Spring Boot</span>
            <span>React</span>
            <span>Docker</span>
            <span>H2 SQL seeds</span>
          </div>
        </div>
      </section>

      <section className="process-flow" aria-label="Generator steps">
        <div className="flow-step">
          <span>01</span>
          <strong>Select project</strong>
        </div>
        <div className="flow-arrow">-&gt;</div>
        <div className="flow-step">
          <span>02</span>
          <strong>Add entities</strong>
        </div>
        <div className="flow-arrow">-&gt;</div>
        <div className="flow-step">
          <span>03</span>
          <strong>Generate ZIP</strong>
        </div>
      </section>

      <section className="builder-shell" id="builder">
        {error && <div className="alert">{error}</div>}

        <div className="builder">
          <aside className="side-panel">
            <div className="mini-logo">
              <img src="/aris033-logo.png" alt="" />
            </div>

            <div className="steps">
              {steps.map((item, index) => (
                <button
                  type="button"
                  key={item}
                  className={step === index ? 'step active' : 'step'}
                  onClick={() => setStep(index)}
                >
                  <span>{index + 1}</span>
                  {item}
                </button>
              ))}
            </div>

            <div className="stats">
              <div>
                <strong>{form.examples.length}</strong>
                <span>entities</span>
              </div>
              <div>
                <strong>{fieldCount}</strong>
                <span>fields</span>
              </div>
            </div>
          </aside>

          <form className="work-card" onSubmit={(event) => event.preventDefault()}>
            {step === 0 && (
              <section className="screen">
                <div className="screen-title">
                  <span>01</span>
                  <h2>Project setup</h2>
                </div>

                <div className="input-grid">
                  <label>
                    Project name
                    <input value={form.project} onChange={(event) => setForm({ ...form, project: event.target.value })} />
                  </label>
                  <label>
                    Group ID
                    <input value={form.groupId} onChange={(event) => setForm({ ...form, groupId: event.target.value })} />
                  </label>
                  <label>
                    Artifact ID
                    <input
                      value={form.artifactId}
                      placeholder="optional"
                      onChange={(event) => setForm({ ...form, artifactId: event.target.value })}
                    />
                  </label>
                  <label>
                    Persistence
                    <select
                      value={form.persistence}
                      onChange={(event) => setForm({ ...form, persistence: event.target.value })}
                    >
                      <option value="h2">H2 + schema/data SQL</option>
                      <option value="memory">Memory repository</option>
                    </select>
                  </label>
                </div>

                <div className="actions">
                  <button type="button" className="secondary" onClick={loadDefaults} disabled={busy}>
                    Load example
                  </button>
                  <button type="button" className="primary" onClick={() => setStep(1)}>
                    Next
                  </button>
                </div>
              </section>
            )}

            {step === 1 && (
              <section className="screen">
                <div className="screen-title">
                  <span>02</span>
                  <h2>Entity builder</h2>
                </div>

                <div className="entity-tabs">
                  {form.examples.map((example, index) => (
                    <button
                      type="button"
                      className={index === activeEntityIndex ? 'entity-tab active' : 'entity-tab'}
                      key={`${example.name}-${index}`}
                      onClick={() => setActiveEntityIndex(index)}
                    >
                      <strong>{example.name || `Entity ${index + 1}`}</strong>
                      <small>{parseFields(example.structure).length} fields</small>
                    </button>
                  ))}
                  <button type="button" className="entity-tab add" onClick={addEntity} disabled={form.examples.length >= 10}>
                    + Entity
                  </button>
                </div>

                <div className="entity-workspace">
                  <div className="entity-header">
                    <label>
                      Entity name
                      <input
                        value={activeEntity.name}
                        onChange={(event) => updateExample(activeEntityIndex, { name: event.target.value })}
                      />
                    </label>
                    <button
                      type="button"
                      className="danger"
                      onClick={() => removeEntity(activeEntityIndex)}
                      disabled={form.examples.length === 1}
                    >
                      Remove
                    </button>
                  </div>

                  <div className="field-composer">
                    <label>
                      New field
                      <input
                        value={newField.name}
                        placeholder="email"
                        onChange={(event) => setNewField({ ...newField, name: event.target.value })}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter') {
                            event.preventDefault();
                            addFieldFromComposer();
                          }
                        }}
                      />
                    </label>
                    <label>
                      Type
                      <select
                        value={newField.type}
                        onChange={(event) => setNewField({ ...newField, type: event.target.value })}
                      >
                        {supportedTypes.map((type) => (
                          <option value={type} key={type}>
                            {type}
                          </option>
                        ))}
                      </select>
                    </label>
                    <button type="button" className="primary" onClick={addFieldFromComposer}>
                      Add field
                    </button>
                  </div>

                  <div className="field-list">
                    {activeFields.map((field, fieldIndex) => (
                      <div className="field-chip" key={`${field.name}-${fieldIndex}`}>
                        <input
                          aria-label="Field name"
                          value={field.name}
                          onChange={(event) => replaceField(fieldIndex, { name: event.target.value })}
                        />
                        <select
                          aria-label="Field type"
                          value={supportedTypes.includes(field.type) ? field.type : 'String'}
                          onChange={(event) => replaceField(fieldIndex, { type: event.target.value })}
                        >
                          {supportedTypes.map((type) => (
                            <option value={type} key={type}>
                              {type}
                            </option>
                          ))}
                        </select>
                        <button
                          type="button"
                          className="remove-field"
                          aria-label={`Remove ${field.name || 'field'}`}
                          onClick={() => removeField(fieldIndex)}
                          disabled={activeFields.length === 1}
                        >
                          x
                        </button>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="actions">
                  <button type="button" className="secondary" onClick={addEntity} disabled={form.examples.length >= 10}>
                    Add another entity
                  </button>
                  <button type="button" className="primary" onClick={previewStructure} disabled={busy}>
                    Preview
                  </button>
                </div>
              </section>
            )}

            {step === 2 && (
              <section className="screen">
                <div className="screen-title">
                  <span>03</span>
                  <h2>Export</h2>
                </div>

                <div className="summary-grid">
                  <div className="summary-card">
                    <strong>{form.project}</strong>
                    <span>{form.groupId}</span>
                  </div>
                  <div className="summary-card">
                    <strong>{form.persistence.toUpperCase()}</strong>
                    <span>{form.persistence === 'h2' ? 'creates + inserts included' : 'in-memory adapters'}</span>
                  </div>
                </div>

                <div className="entity-map">
                  {form.examples.map((example) => (
                    <div className="entity-map-card" key={example.name}>
                      <strong>{example.name}</strong>
                      <span>{parseFields(example.structure).map((field) => `${field.name}:${field.type}`).join(' · ')}</span>
                    </div>
                  ))}
                </div>

                {preview && (
                  <div className="folder-preview">
                    {preview.folders.map((folder) => (
                      <code key={folder}>{folder}</code>
                    ))}
                  </div>
                )}

                <div className="actions">
                  <button type="button" className="secondary" onClick={previewStructure} disabled={busy}>
                    Refresh preview
                  </button>
                  <button type="button" className="primary" onClick={generateZip} disabled={busy}>
                    Download ZIP
                  </button>
                </div>
              </section>
            )}
          </form>
        </div>
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
  return fields.length ? fields : [{ name: 'name', type: 'String' }];
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
