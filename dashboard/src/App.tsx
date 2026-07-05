import React, { useEffect, useState, useRef } from 'react';
import { 
  auth, 
  googleProvider, 
  db, 
  signInWithPopup, 
  signOut 
} from './firebase';
import { onAuthStateChanged, type User } from 'firebase/auth';
import { doc, collection, addDoc, onSnapshot, Timestamp } from 'firebase/firestore';

// --- Icons ---
const ServerIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <rect width="20" height="8" x="2" y="2" rx="2" ry="2"/>
    <rect width="20" height="8" x="2" y="14" rx="2" ry="2"/>
    <line x1="6" x2="6.01" y1="6" y2="6"/>
    <line x1="6" x2="6.01" y1="18" y2="18"/>
  </svg>
);

const UsersIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/>
    <circle cx="9" cy="7" r="4"/>
    <path d="M22 21v-2a4 4 0 0 0-3-3.87"/>
    <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
  </svg>
);

const ConsoleIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="4 17 10 11 4 5"/>
    <line x1="12" x2="20" y1="19" y2="19"/>
  </svg>
);

const BotIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 8V4H8"/>
    <rect width="16" height="12" x="4" y="8" rx="2"/>
    <path d="M2 14h2"/>
    <path d="M20 14h2"/>
    <path d="M15 13v2"/>
    <path d="M9 13v2"/>
  </svg>
);

const ShieldIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <path d="M20 13c0 5-3.5 7.5-7.66 9.7a1 1 0 0 1-.68 0C7.5 20.5 4 18 4 13V6a1 1 0 0 1 .76-.97l8-2a1 1 0 0 1 .48 0l8 2A1 1 0 0 1 20 6z"/>
  </svg>
);

const GlobeIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="10"/>
    <line x1="2" x2="22" y1="12" y2="12"/>
    <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
  </svg>
);

const PowerIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 2v10"/>
    <path d="M18.4 6.6a9 9 0 1 1-12.77.04"/>
  </svg>
);

// --- Type definitions ---
interface Player {
  name: string;
  uuid: string;
}

interface DashboardStatus {
  serverRunning: boolean;
  playersOnline: Player[];
  uptimeSeconds: number;
  tps: number | null;
  afkBotEnabled: boolean;
  subdomain: string | null;
  whitelist: string[];
  lastSeen: Timestamp;
}

interface ToastMessage {
  id: string;
  type: 'success' | 'error';
  text: string;
}

interface ConsoleLine {
  type: 'input' | 'output' | 'error';
  text: string;
}

function App() {
  const [user, setUser] = useState<User | null>(null);
  const [authLoading, setAuthLoading] = useState(true);
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  // Toast Helper
  const showToast = (type: 'success' | 'error', text: string) => {
    const id = Math.random().toString(36).substring(2, 9);
    setToasts(prev => [...prev, { id, type, text }]);
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id));
    }, 4000);
  };

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, (currentUser) => {
      setUser(currentUser);
      setAuthLoading(false);
    });
    return unsubscribe;
  }, []);

  const handleLogin = async () => {
    try {
      await signInWithPopup(auth, googleProvider);
      showToast('success', 'Logged in successfully!');
    } catch (e: any) {
      showToast('error', e.message || 'Login failed.');
    }
  };

  const handleLogout = async () => {
    try {
      await signOut(auth);
      showToast('success', 'Logged out.');
    } catch (e: any) {
      showToast('error', e.message || 'Logout failed.');
    }
  };

  if (authLoading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', fontFamily: 'var(--font-sans)', color: 'var(--text-secondary)' }}>
        Loading Session...
      </div>
    );
  }

  return (
    <>
      {user ? (
        <DashboardPage user={user} onLogout={handleLogout} showToast={showToast} />
      ) : (
        <LoginPage onLogin={handleLogin} />
      )}

      {/* Toast Render */}
      <div className="toast-container">
        {toasts.map(t => (
          <div key={t.id} className={`toast ${t.type === 'error' ? 'toast-error' : 'toast-success'}`}>
            <span style={{ fontSize: '16px' }}>{t.type === 'error' ? '❌' : '✅'}</span>
            <span>{t.text}</span>
          </div>
        ))}
      </div>
    </>
  );
}

// --- Login Page Component ---
function LoginPage({ onLogin }: { onLogin: () => void }) {
  return (
    <div className="auth-container">
      <div className="auth-card">
        <h1 className="auth-title">PocketCraft</h1>
        <p className="auth-subtitle">Server Web Control Dashboard</p>
        <p style={{ color: 'var(--text-secondary)', fontSize: '14px', marginBottom: '24px', lineHeight: '1.5' }}>
          Connect securely to control your Minecraft server, players, AFK bots, and domains directly from your browser.
        </p>
        <button className="btn btn-primary" onClick={onLogin} style={{ width: '100%', padding: '14px' }}>
          Sign In with Google
        </button>
      </div>
    </div>
  );
}

// --- Dashboard Page Component ---
function DashboardPage({ 
  user, 
  onLogout, 
  showToast 
}: { 
  user: User; 
  onLogout: () => void; 
  showToast: (type: 'success' | 'error', text: string) => void; 
}) {
  const [status, setStatus] = useState<DashboardStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [phoneOnline, setPhoneOnline] = useState(false);
  const [proUser, setProUser] = useState(false);

  // Command Pending States
  const [pendingActions, setPendingActions] = useState<Record<string, boolean>>({});

  // Confirm Modals State
  const [confirmModal, setConfirmModal] = useState<{
    show: boolean;
    title: string;
    description: string;
    onConfirm: () => void;
  } | null>(null);

  // Snapshot Listeners
  useEffect(() => {
    // 1. Listen to dashboard status
    const statusRef = doc(db, 'users', user.uid, 'dashboard_status', 'status');
    const unsubscribeStatus = onSnapshot(statusRef, (snapshot) => {
      if (snapshot.exists()) {
        const data = snapshot.data() as DashboardStatus;
        setStatus(data);
      } else {
        setStatus(null);
      }
      setLoading(false);
    }, (error) => {
      console.error("Error listening to status:", error);
      showToast('error', 'Error sync status.');
      setLoading(false);
    });

    // 2. Fetch User Entitlement State
    const userDocRef = doc(db, 'users', user.uid);
    const unsubscribeUser = onSnapshot(userDocRef, (snapshot) => {
      if (snapshot.exists()) {
        const tier = snapshot.data().premiumTier || 'none';
        setProUser(tier === 'premium' || tier === 'supportive');
      } else {
        setProUser(false);
      }
    });

    return () => {
      unsubscribeStatus();
      unsubscribeUser();
    };
  }, [user.uid]);

  // Heartbeat loop check (Phone offline if now - lastSeen > 30s)
  useEffect(() => {
    if (!status) {
      setPhoneOnline(false);
      return;
    }
    const checkOnline = () => {
      const lastSeenMillis = status.lastSeen?.toMillis() || 0;
      const diff = Date.now() - lastSeenMillis;
      setPhoneOnline(diff <= 30000);
    };

    checkOnline();
    const interval = setInterval(checkOnline, 5000);
    return () => clearInterval(interval);
  }, [status]);

  // Dispatch Command Utility
  const dispatchCommand = async (type: string, payload: Record<string, any> = {}) => {
    const actionKey = `${type}_${JSON.stringify(payload)}`;
    setPendingActions(prev => ({ ...prev, [actionKey]: true }));

    try {
      const cmdRef = await addDoc(collection(db, 'users', user.uid, 'dashboard_commands'), {
        type,
        payload,
        createdAt: Timestamp.now(),
        status: 'pending',
        result: null,
        errorMessage: null
      });

      // Listen for command result
      return new Promise<string>((resolve, reject) => {
        const unsub = onSnapshot(cmdRef, (snapshot) => {
          if (snapshot.exists()) {
            const data = snapshot.data();
            if (data.status === 'done') {
              unsub();
              setPendingActions(prev => ({ ...prev, [actionKey]: false }));
              resolve(data.result || 'OK');
            } else if (data.status === 'failed') {
              unsub();
              setPendingActions(prev => ({ ...prev, [actionKey]: false }));
              reject(new Error(data.errorMessage || 'Command failed.'));
            }
          }
        });
      });
    } catch (e: any) {
      setPendingActions(prev => ({ ...prev, [actionKey]: false }));
      throw e;
    }
  };

  const handleAction = async (type: string, payload: Record<string, any> = {}, successMsg?: string) => {
    const actionKey = `${type}_${JSON.stringify(payload)}`;
    if (pendingActions[actionKey]) return;

    try {
      const res = await dispatchCommand(type, payload);
      if (successMsg) showToast('success', successMsg);
      return res;
    } catch (e: any) {
      showToast('error', e.message || 'Action failed.');
    }
  };

  const triggerConfirm = (title: string, description: string, onConfirm: () => void) => {
    setConfirmModal({
      show: true,
      title,
      description,
      onConfirm: () => {
        onConfirm();
        setConfirmModal(null);
      }
    });
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', fontFamily: 'var(--font-sans)', color: 'var(--text-secondary)' }}>
        Synchronizing Status...
      </div>
    );
  }

  // No status document yet
  if (!status) {
    return (
      <div className="auth-container">
        <div className="auth-card" style={{ maxWidth: '500px' }}>
          <h2 className="auth-title" style={{ marginBottom: '16px' }}>No Server Found</h2>
          <p style={{ color: 'var(--text-secondary)', fontSize: '14px', marginBottom: '24px', lineHeight: '1.6' }}>
            Open **PocketCraft** on your phone, sign in with this Google account ({user.email}), and start hosting your Minecraft server to activate this web dashboard.
          </p>
          <button className="btn btn-secondary" onClick={onLogout}>Sign Out</button>
        </div>
      </div>
    );
  }

  return (
    <div className="dashboard-container">
      {/* Header */}
      <header className="dashboard-header">
        <div className="brand-section">
          <h1 className="brand-logo">PocketCraft</h1>
          <div className={`status-pill ${phoneOnline ? 'status-online' : 'status-offline'}`}>
            <span className="status-dot"></span>
            <span>{phoneOnline ? 'Online' : 'Phone Offline'}</span>
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
          <span style={{ fontSize: '14px', color: 'var(--text-secondary)' }}>{user.email}</span>
          <button className="btn btn-secondary" onClick={onLogout}>Sign Out</button>
        </div>
      </header>

      {/* Grid Layout */}
      <div className="dashboard-grid">
        {/* Server Control Card */}
        <div className="panel-card" style={{ gridColumn: 'span 7' }}>
          <h2 className="card-title"><ServerIcon /> Server Control</h2>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '24px', alignItems: 'center' }}>
            <div>
              <p style={{ fontSize: '14px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Server Status</p>
              <h3 style={{ fontSize: '24px', fontWeight: '800', color: status.serverRunning ? 'var(--success-color)' : 'var(--text-secondary)' }}>
                {status.serverRunning ? 'Running' : 'Stopped'}
              </h3>
            </div>
            {status.serverRunning && (
              <>
                <div>
                  <p style={{ fontSize: '14px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Uptime</p>
                  <h3 style={{ fontSize: '20px', fontWeight: '700' }}>
                    {formatUptime(status.uptimeSeconds)}
                  </h3>
                </div>
                <div>
                  <p style={{ fontSize: '14px', color: 'var(--text-secondary)', marginBottom: '4px' }}>TPS</p>
                  <h3 style={{ fontSize: '20px', fontWeight: '700', color: status.tps && status.tps < 18 ? 'var(--warning-color)' : 'var(--success-color)' }}>
                    {status.tps ? status.tps.toFixed(1) : '20.0'}
                  </h3>
                </div>
              </>
            )}
          </div>

          <div style={{ display: 'flex', gap: '12px' }}>
            {status.serverRunning ? (
              <button 
                className="btn btn-danger" 
                onClick={() => triggerConfirm(
                  'Stop Server', 
                  'Are you sure you want to stop the Minecraft server? This will disconnect all online players.',
                  () => handleAction('stop_server', {}, 'Server stop initiated.')
                )}
                disabled={!phoneOnline || pendingActions['stop_server_{}']}
              >
                <PowerIcon /> {pendingActions['stop_server_{}'] ? 'Stopping...' : 'Stop Server'}
              </button>
            ) : (
              <button 
                className="btn btn-primary" 
                onClick={() => handleAction('start_server', {}, 'Server start initiated.')}
                disabled={!phoneOnline || pendingActions['start_server_{}']}
              >
                <PowerIcon /> {pendingActions['start_server_{}'] ? 'Starting...' : 'Start Server'}
              </button>
            )}
          </div>
        </div>

        {/* AFK Helper Card */}
        <div className="panel-card" style={{ gridColumn: 'span 5' }}>
          <h2 className="card-title"><BotIcon /> AFK Helper Bots</h2>
          <p style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '20px', lineHeight: '1.5' }}>
            Spawn dummy players in-game to keep chunks loaded and farm operations active.
          </p>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'rgba(255,255,255,0.03)', padding: '14px 18px', borderRadius: '12px', border: '1px solid rgba(255,255,255,0.04)' }}>
            <div>
              <p style={{ fontWeight: '700', fontSize: '14px' }}>All AFK Bots</p>
              <p style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Status on active world</p>
            </div>
            <label className="switch">
              <input 
                type="checkbox" 
                checked={status.afkBotEnabled} 
                onChange={(e) => handleAction('toggle_afk_bot', { enabled: e.target.checked }, `AFK bot status set to ${e.target.checked}`)}
                disabled={!phoneOnline || !status.serverRunning || pendingActions[`toggle_afk_bot_${JSON.stringify({ enabled: !status.afkBotEnabled })}`]}
              />
              <span className="slider"></span>
            </label>
          </div>
        </div>

        {/* Players Online Card */}
        <div className="panel-card" style={{ gridColumn: 'span 6' }}>
          <h2 className="card-title"><UsersIcon /> Players Online ({status.playersOnline.length})</h2>
          <div className="player-list">
            {status.playersOnline.length === 0 ? (
              <div style={{ color: 'var(--text-muted)', fontSize: '13px', textAlign: 'center', padding: '20px' }}>
                No players currently connected.
              </div>
            ) : (
              status.playersOnline.map(p => (
                <div key={p.uuid || p.name} className="player-item">
                  <div className="player-name-wrapper">
                    <span className="player-name">{p.name}</span>
                    <span className={`player-badge ${p.name.startsWith('.') ? 'badge-bedrock' : 'badge-java'}`}>
                      {p.name.startsWith('.') ? 'Bedrock' : 'Java'}
                    </span>
                  </div>
                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button 
                      className="btn btn-secondary" 
                      style={{ padding: '6px 12px', fontSize: '12px' }}
                      onClick={() => handleAction('kick', { playerName: p.name }, `Kicked player ${p.name}`)}
                      disabled={!phoneOnline || !status.serverRunning}
                    >
                      Kick
                    </button>
                    <button 
                      className="btn btn-danger" 
                      style={{ padding: '6px 12px', fontSize: '12px' }}
                      onClick={() => triggerConfirm(
                        'Ban Player',
                        `Are you sure you want to permanently ban player ${p.name} from the server?`,
                        () => handleAction('ban', { playerName: p.name }, `Banned player ${p.name}`)
                      )}
                      disabled={!phoneOnline || !status.serverRunning}
                    >
                      Ban
                    </button>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Whitelist Card */}
        <WhitelistCardComponent 
          whitelist={status.whitelist} 
          phoneOnline={phoneOnline} 
          serverRunning={status.serverRunning}
          onDispatch={dispatchCommand}
        />

        {/* Console Command Card */}
        <ConsoleCardComponent 
          phoneOnline={phoneOnline} 
          serverRunning={status.serverRunning}
          onDispatch={dispatchCommand}
        />

        {/* Custom Subdomain Card */}
        <SubdomainCardComponent 
          currentSubdomain={status.subdomain}
          proUser={proUser}
          phoneOnline={phoneOnline}
          onDispatch={dispatchCommand}
          showToast={showToast}
        />
      </div>

      {/* Confirmation Modal */}
      {confirmModal && confirmModal.show && (
        <div className="modal-overlay">
          <div className="modal-content">
            <h3 className="modal-title">{confirmModal.title}</h3>
            <p className="modal-desc">{confirmModal.description}</p>
            <div className="modal-actions">
              <button className="btn btn-secondary" onClick={() => setConfirmModal(null)}>Cancel</button>
              <button className="btn btn-danger" onClick={confirmModal.onConfirm}>Confirm Action</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// --- Console Card Sub-component ---
function ConsoleCardComponent({ 
  phoneOnline, 
  serverRunning, 
  onDispatch 
}: { 
  phoneOnline: boolean; 
  serverRunning: boolean; 
  onDispatch: (type: string, payload: Record<string, any>) => Promise<string>; 
}) {
  const [lines, setLines] = useState<ConsoleLine[]>([
    { type: 'output', text: 'Welcome to PocketCraft Command Prompt.' },
    { type: 'output', text: 'Commands are sent via RCON client link.' }
  ]);
  const [cmdInput, setCmdInput] = useState('');
  const [running, setRunning] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [lines]);

  const runCommand = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanCmd = cmdInput.trim();
    if (!cleanCmd || running || !phoneOnline || !serverRunning) return;

    setRunning(true);
    setLines(prev => [...prev, { type: 'input', text: `> ${cleanCmd}` }]);
    setCmdInput('');

    try {
      const response = await onDispatch('rcon', { command: cleanCmd });
      setLines(prev => [...prev, { type: 'output', text: response }]);
    } catch (err: any) {
      setLines(prev => [...prev, { type: 'error', text: err.message || 'RCON connection failure.' }]);
    } finally {
      setRunning(false);
    }
  };

  return (
    <div className="panel-card" style={{ gridColumn: 'span 7' }}>
      <h2 className="card-title"><ConsoleIcon /> RCON Console Command</h2>
      <div className="console-wrapper">
        <div className="console-scrollback" ref={scrollRef}>
          {lines.map((l, i) => (
            <div key={i} className={`console-line ${l.type}`}>
              {l.text}
            </div>
          ))}
          {running && <div className="console-line output" style={{ opacity: 0.5 }}>Processing command...</div>}
        </div>
        <form className="console-input-row" onSubmit={runCommand}>
          <input 
            type="text" 
            placeholder={!serverRunning ? "Start server to enter commands..." : "Enter command (e.g. op username, time set day)..."}
            value={cmdInput} 
            onChange={(e) => setCmdInput(e.target.value)}
            disabled={!phoneOnline || !serverRunning || running}
          />
          <button type="submit" disabled={!phoneOnline || !serverRunning || running || !cmdInput.trim()}>
            Run
          </button>
        </form>
      </div>
    </div>
  );
}

// --- Whitelist Card Sub-component ---
function WhitelistCardComponent({
  whitelist,
  phoneOnline,
  serverRunning,
  onDispatch
}: {
  whitelist: string[];
  phoneOnline: boolean;
  serverRunning: boolean;
  onDispatch: (type: string, payload: Record<string, any>) => Promise<string>;
}) {
  const [playerNameInput, setPlayerNameInput] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    const name = playerNameInput.trim();
    if (!name || submitting || !phoneOnline || !serverRunning) return;

    setSubmitting(true);
    try {
      await onDispatch('whitelist_add', { playerName: name });
      setPlayerNameInput('');
    } catch (e) {
      console.error(e);
    } finally {
      setSubmitting(false);
    }
  };

  const handleRemove = async (name: string) => {
    if (submitting || !phoneOnline || !serverRunning) return;
    setSubmitting(true);
    try {
      await onDispatch('whitelist_remove', { playerName: name });
    } catch (e) {
      console.error(e);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="panel-card" style={{ gridColumn: 'span 6' }}>
      <h2 className="card-title"><ShieldIcon /> Whitelist Manager ({whitelist.length})</h2>
      <form onSubmit={handleAdd} style={{ display: 'flex', gap: '10px', marginBottom: '16px' }}>
        <input 
          type="text" 
          className="form-control" 
          placeholder="Player username..." 
          value={playerNameInput}
          onChange={(e) => setPlayerNameInput(e.target.value)}
          disabled={!phoneOnline || !serverRunning || submitting}
          style={{ flex: 1, padding: '10px 14px' }}
        />
        <button 
          type="submit" 
          className="btn btn-primary"
          disabled={!phoneOnline || !serverRunning || submitting || !playerNameInput.trim()}
        >
          {submitting ? '...' : 'Add'}
        </button>
      </form>
      <div className="player-list" style={{ maxHeight: '180px' }}>
        {whitelist.length === 0 ? (
          <div style={{ color: 'var(--text-muted)', fontSize: '13px', textAlign: 'center', padding: '15px' }}>
            Whitelist is empty.
          </div>
        ) : (
          whitelist.map(name => (
            <div key={name} className="player-item" style={{ padding: '8px 14px' }}>
              <span className="player-name">{name}</span>
              <button 
                className="btn btn-secondary" 
                style={{ padding: '4px 8px', fontSize: '11px' }}
                onClick={() => handleRemove(name)}
                disabled={!phoneOnline || !serverRunning || submitting}
              >
                Remove
              </button>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

// --- Custom Subdomain Card Sub-component ---
function SubdomainCardComponent({
  currentSubdomain,
  proUser,
  phoneOnline,
  onDispatch,
  showToast
}: {
  currentSubdomain: string | null;
  proUser: boolean;
  phoneOnline: boolean;
  onDispatch: (type: string, payload: Record<string, any>) => Promise<string>;
  showToast: (type: 'success' | 'error', text: string) => void;
}) {
  const [subdomainInput, setSubdomainInput] = useState(currentSubdomain || '');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setSubdomainInput(currentSubdomain || '');
  }, [currentSubdomain]);

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!proUser || saving || !phoneOnline) return;

    const val = subdomainInput.trim().toLowerCase();
    if (!/^[a-z0-9-]{3,32}$/.test(val)) {
      showToast('error', 'Must be 3-32 characters using lowercase letters, numbers, or hyphens.');
      return;
    }

    setSaving(true);
    try {
      await onDispatch('set_subdomain', { subdomain: val });
      showToast('success', 'Custom subdomain updated! Restart server to apply.');
    } catch (e: any) {
      showToast('error', e.message || 'Could not update subdomain.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="panel-card" style={{ gridColumn: 'span 5' }}>
      <h2 className="card-title"><GlobeIcon /> Custom IP Subdomain</h2>
      
      {!proUser ? (
        <div style={{ textAlign: 'center', padding: '10px' }}>
          <p style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '14px' }}>
            Custom subdomain customization requires a Pro or Member tier.
          </p>
          <div className="status-pill status-offline" style={{ padding: '8px 16px', borderRadius: '8px', fontSize: '11px' }}>
            🔒 Feature Locked
          </div>
        </div>
      ) : (
        <form onSubmit={handleSave} className="form-group">
          <p style={{ fontSize: '12px', color: 'var(--text-secondary)', lineHeight: '1.4' }}>
            Customize your relay routing IP (e.g. `yourname.as.pocketcraft.online`).
          </p>
          <div style={{ display: 'flex', gap: '10px' }}>
            <input 
              type="text" 
              className="form-control" 
              placeholder="subdomain" 
              value={subdomainInput}
              onChange={(e) => setSubdomainInput(e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ''))}
              disabled={saving || !phoneOnline}
              style={{ flex: 1 }}
            />
            <button 
              type="submit" 
              className="btn btn-primary"
              disabled={saving || !phoneOnline || subdomainInput.trim() === (currentSubdomain || '')}
            >
              {saving ? 'Saving...' : 'Save'}
            </button>
          </div>
          <p className="helper-text">
            Current IP: <code style={{ fontFamily: 'var(--font-mono)', color: 'var(--accent-color)' }}>
              {currentSubdomain ? `${currentSubdomain}.pocketcraft.online` : 'None'}
            </code>
          </p>
        </form>
      )}
    </div>
  );
}

// --- Helper Functions ---
function formatUptime(seconds: number): string {
  if (!seconds || seconds <= 0) return '0s';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  if (h > 0) return `${h}h ${m}m ${s}s`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
}

export default App;
