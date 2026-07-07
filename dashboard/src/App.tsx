import React, { useEffect, useState, useRef } from 'react';
import { 
  auth, 
  googleProvider, 
  db, 
  signInWithPopup, 
  signOut 
} from './firebase';
import { onAuthStateChanged, signInWithEmailAndPassword, createUserWithEmailAndPassword, type User } from 'firebase/auth';
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
  pingText?: string;
}

interface AfkBot {
  id: string;
  name: string;
  dummyName: string;
  x: number;
  y: number;
  z: number;
  world: string;
  active: boolean;
  owner: string;
  ownerUuid: string;
}

interface DashboardStatus {
  serverRunning: boolean;
  serverState?: 'running' | 'starting' | 'stopped';
  bootProgress?: string;
  bootProgressPercent?: number;
  playersOnline: Player[];
  uptimeSeconds: number;
  tps: number | null;
  afkBotEnabled: boolean;
  subdomain: string | null;
  whitelist: string[];
  lastSeen: Timestamp;
  relayAddress?: string | null;
  localIp?: string;
  afkBots?: AfkBot[];
  allPlayers?: any[];
  currentWorld?: string;
  worlds?: string[];
  properties?: {
    difficulty?: string;
    gamemode?: string;
    pvp?: string;
    maxPlayers?: string;
    viewDistance?: string;
    simulationDistance?: string;
    allowNether?: string;
    whiteList?: string;
    spawnProtection?: string;
    levelSeed?: string;
    hardcore?: string;
    spawnMonsters?: string;
    generateStructures?: string;
    worldDisplayName?: string;
    worldDescription?: string;
  };
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

// --- Subtle Background Mobs Component ---
function SubtleBackgroundMobs() {
  const mobs = [
    {
      name: 'Creeper',
      d: 'M2 2h20v20H2V2zm4 4v4h4V6H6zm10 0v4h4V6h-4zm-6 6h4v2h-4v-2zm-2 2h8v4H6v-4z',
      color: '#22c55e'
    }
  ];

  const [items] = useState(() => {
    return Array.from({ length: 12 }).map((_, i) => {
      const mob = mobs[i % mobs.length];
      return {
        id: i,
        mob,
        left: `${(i * 9) % 95}%`,
        delay: `${i * 4}s`,
        size: `${24 + (i * 7) % 24}px`,
        duration: `${35 + (i * 8) % 30}s`
      };
    });
  });

  return (
    <div className="subtle-bg-mobs">
      {items.map(item => (
        <svg
          key={item.id}
          className="floating-mob"
          style={{
            left: item.left,
            width: item.size,
            height: item.size,
            animationDelay: item.delay,
            animationDuration: item.duration,
            color: item.mob.color
          }}
          viewBox="0 0 24 24"
        >
          <path d={item.mob.d} />
        </svg>
      ))}
    </div>
  );
}

function App() {
  const [user, setUser] = useState<User | null>(null);
  const [authLoading, setAuthLoading] = useState(true);
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  // Email auth states
  const [emailInput, setEmailInput] = useState('');
  const [passwordInput, setPasswordInput] = useState('');
  const [isRegisterMode, setIsRegisterMode] = useState(false);

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

  const handleEmailAuth = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!emailInput.trim() || !passwordInput.trim()) {
      showToast('error', 'Please fill in all fields.');
      return;
    }
    try {
      if (isRegisterMode) {
        await createUserWithEmailAndPassword(auth, emailInput.trim(), passwordInput.trim());
        showToast('success', 'Account registered successfully!');
      } else {
        await signInWithEmailAndPassword(auth, emailInput.trim(), passwordInput.trim());
        showToast('success', 'Logged in successfully!');
      }
    } catch (e: any) {
      showToast('error', e.message || 'Authentication failed.');
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
        <LoginPage 
          onLogin={handleLogin}
          emailInput={emailInput}
          setEmailInput={setEmailInput}
          passwordInput={passwordInput}
          setPasswordInput={setPasswordInput}
          isRegisterMode={isRegisterMode}
          setIsRegisterMode={setIsRegisterMode}
          handleEmailAuth={handleEmailAuth}
        />
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
function LoginPage({ 
  onLogin,
  emailInput,
  setEmailInput,
  passwordInput,
  setPasswordInput,
  isRegisterMode,
  setIsRegisterMode,
  handleEmailAuth
}: { 
  onLogin: () => void;
  emailInput: string;
  setEmailInput: (v: string) => void;
  passwordInput: string;
  setPasswordInput: (v: string) => void;
  isRegisterMode: boolean;
  setIsRegisterMode: (v: boolean) => void;
  handleEmailAuth: (e: React.FormEvent) => void;
}) {
  return (
    <div className="auth-container">
      <div className="auth-card" style={{ maxWidth: '400px', width: '90%' }}>
        <h1 className="auth-title">PocketCraft</h1>
        <p className="auth-subtitle" style={{ marginBottom: '24px' }}>Server Web Control Dashboard</p>
        
        <form onSubmit={handleEmailAuth} style={{ display: 'flex', flexDirection: 'column', gap: '14px', textAlign: 'left' }}>
          <div className="form-group" style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
            <label style={{ fontSize: '12px', color: 'var(--text-secondary)', fontWeight: '600' }}>Email Address</label>
            <input 
              type="email" 
              className="form-control"
              placeholder="your@email.com"
              value={emailInput}
              onChange={(e) => setEmailInput(e.target.value)}
              style={{ padding: '10px 12px', borderRadius: '8px', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--panel-border)', color: 'var(--text-primary)' }}
              required
            />
          </div>
          
          <div className="form-group" style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
            <label style={{ fontSize: '12px', color: 'var(--text-secondary)', fontWeight: '600' }}>Password</label>
            <input 
              type="password" 
              className="form-control"
              placeholder="••••••••"
              value={passwordInput}
              onChange={(e) => setPasswordInput(e.target.value)}
              style={{ padding: '10px 12px', borderRadius: '8px', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--panel-border)', color: 'var(--text-primary)' }}
              required
            />
          </div>

          <button type="submit" className="btn btn-primary" style={{ width: '100%', padding: '12px', marginTop: '6px', fontSize: '14px', fontWeight: '700' }}>
            {isRegisterMode ? 'Register & Sign In' : 'Sign In'}
          </button>
          
          <div style={{ textAlign: 'center', marginTop: '4px' }}>
            <button 
              type="button" 
              style={{ background: 'none', border: 'none', color: 'var(--accent-color)', fontSize: '12px', cursor: 'pointer', textDecoration: 'underline' }}
              onClick={() => setIsRegisterMode(!isRegisterMode)}
            >
              {isRegisterMode ? 'Already have an account? Sign In' : "Don't have an account? Register"}
            </button>
          </div>
        </form>

        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', margin: '24px 0 16px 0' }}>
          <div style={{ flex: 1, height: '1px', background: 'var(--panel-border)' }} />
          <span style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase' }}>or</span>
          <div style={{ flex: 1, height: '1px', background: 'var(--panel-border)' }} />
        </div>

        <button 
          className="btn btn-secondary" 
          onClick={onLogin} 
          style={{ width: '100%', padding: '12px', fontSize: '14px', fontWeight: '700', background: 'rgba(255,255,255,0.05)', color: 'var(--text-primary)', border: '1px solid var(--panel-border)' }}
        >
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
  const [dashboardSecret, setDashboardSecret] = useState('');
  const [profilePlayer, setProfilePlayer] = useState<any | null>(null);

  // Command Pending States
  const [pendingActions, setPendingActions] = useState<Record<string, boolean>>({});

  // Confirm Modals State
  const [confirmModal, setConfirmModal] = useState<{
    show: boolean;
    title: string;
    description: string;
    onConfirm: () => void;
  } | null>(null);

  // Local slider states for immediate UI updates
  const [sliderMaxPlayers, setSliderMaxPlayers] = useState<string | null>(null);
  const [sliderSpawnProtection, setSliderSpawnProtection] = useState<string | null>(null);
  const [sliderViewDistance, setSliderViewDistance] = useState<string | null>(null);
  const [sliderSimulationDistance, setSliderSimulationDistance] = useState<string | null>(null);
  const [tpDestination, setTpDestination] = useState('');
  const [newBotName, setNewBotName] = useState('');
  const [newBotX, setNewBotX] = useState('');
  const [newBotY, setNewBotY] = useState('');
  const [newBotZ, setNewBotZ] = useState('');

  // Snapshot Listeners
  useEffect(() => {
    // 1. Listen to dashboard status
    const statusRef = doc(db, 'users', user.uid, 'dashboard_status', 'status');
    const unsubscribeStatus = onSnapshot(statusRef, (snapshot) => {
      if (snapshot.exists()) {
        const data = snapshot.data() as DashboardStatus;
        setStatus(data);
        setSliderMaxPlayers(null);
        setSliderSpawnProtection(null);
        setSliderViewDistance(null);
        setSliderSimulationDistance(null);
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
        const data = snapshot.data();
        const tier = data.premiumTier || 'none';
        setProUser(tier === 'premium' || tier === 'supportive');
        setDashboardSecret(data.dashboardSecret || '');
      } else {
        setProUser(false);
        setDashboardSecret('');
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
        secret: dashboardSecret,
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

  const resolvedServerState = status.serverState || (status.serverRunning ? 'running' : 'stopped');
  const rawBootProgress = status.bootProgress?.trim() || '';
  const visibleBootProgress = resolvedServerState === 'starting'
    ? (rawBootProgress.toLowerCase().includes('stopping') ? 'Launching Java VM runtime...' : rawBootProgress || 'Launching Java VM runtime...')
    : '';
  const bootProgressPercent = resolvedServerState === 'starting'
    ? Math.max(0, Math.min(100, status.bootProgressPercent || 0))
    : 0;
  const serverStatusLabel = resolvedServerState === 'running'
    ? 'Running'
    : resolvedServerState === 'starting'
      ? 'Starting...'
      : 'Stopped';
  const serverStatusColor = resolvedServerState === 'running'
    ? 'var(--success-color)'
    : resolvedServerState === 'starting'
      ? 'var(--warning-color)'
      : 'var(--text-secondary)';

  // Combine online players and all offline players from allPlayers
  const allJoinedPlayers = (() => {
    const onlineMap = new Map(status.playersOnline.map(p => [p.name, p]));
    const offlineList = (status.allPlayers || []).map(p => ({
      name: p.name,
      uuid: p.uuid,
      online: onlineMap.has(p.name),
      pingText: onlineMap.get(p.name)?.pingText || '',
      ...p
    }));

    // Fallback to online players if allPlayers is empty
    if (offlineList.length === 0) {
      return status.playersOnline.map(p => ({
        name: p.name,
        uuid: p.uuid,
        online: true,
        pingText: p.pingText || ''
      }));
    }

    // Sort by online status (true first), then name
    return offlineList.sort((a, b) => {
      if (a.online && !b.online) return -1;
      if (!a.online && b.online) return 1;
      return a.name.localeCompare(b.name);
    });
  })();

  const maxPlayersVal = sliderMaxPlayers ?? status.properties?.maxPlayers ?? '10';
  const spawnProtectionVal = sliderSpawnProtection ?? status.properties?.spawnProtection ?? '16';
  const viewDistanceVal = sliderViewDistance ?? status.properties?.viewDistance ?? '10';
  const simulationDistanceVal = sliderSimulationDistance ?? status.properties?.simulationDistance ?? '10';

  return (
    <div className="dashboard-container">
      <SubtleBackgroundMobs />
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
        {!phoneOnline && (
          <div className="panel-card" style={{ gridColumn: 'span 12', background: 'rgba(239, 68, 68, 0.08)', border: '1px solid rgba(239, 68, 68, 0.24)', borderRadius: '16px', display: 'flex', flexDirection: 'column', gap: '8px', padding: '16px 20px', animation: 'fadeIn 0.3s ease-out' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', color: 'var(--danger-color)', fontWeight: '800', fontSize: '16px' }}>
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/>
                <line x1="12" y1="9" x2="12" y2="13"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
              Troubleshooting Connection Notice (Phone is Offline)
            </div>
            <p style={{ fontSize: '14px', color: 'var(--text-secondary)', lineHeight: '1.6' }}>
              Your phone appears to be offline. To ensure the PocketCraft Web Dashboard can access your phone at any time without keeping the app open, please disable battery optimization for the app:
            </p>
            <ol style={{ fontSize: '13px', color: 'var(--text-secondary)', paddingLeft: '20px', lineHeight: '1.6', display: 'flex', flexDirection: 'column', gap: '4px' }}>
              <li>Open <strong>Settings</strong> on your phone.</li>
              <li>Navigate to <strong>Apps</strong> → <strong>PocketCraft</strong> → <strong>Battery</strong>.</li>
              <li>Set the battery usage to <strong>"Unrestricted"</strong> (disable optimization).</li>
              <li>Enable <strong>"Always Alive in Background"</strong> inside the app settings tab.</li>
            </ol>
          </div>
        )}
        {/* Server Control Card */}
        <div className="panel-card" style={{ gridColumn: 'span 8' }}>
          <h2 className="card-title"><ServerIcon /> Server Control</h2>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '16px', alignItems: 'center' }}>
            <div>
              <p style={{ fontSize: '14px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Server Status</p>
              <h3 style={{ fontSize: '24px', fontWeight: '800', color: serverStatusColor }}>
                {serverStatusLabel}
              </h3>
            </div>
            {resolvedServerState === 'running' && (
              <div>
                <p style={{ fontSize: '14px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Uptime</p>
                <h3 style={{ fontSize: '20px', fontWeight: '700' }}>
                  {formatUptime(status.uptimeSeconds)}
                </h3>
              </div>
            )}
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '12px', marginBottom: '20px' }}>
            <div style={{ background: 'rgba(255,255,255,0.02)', padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--panel-border)', minWidth: 0 }}>
              <p style={{ fontSize: '10px', color: 'var(--text-secondary)', textTransform: 'uppercase', marginBottom: '4px', margin: 0 }}>Relay IP Address</p>
              <p 
                style={{ fontSize: '11px', fontWeight: '700', color: 'var(--accent-color)', fontFamily: 'var(--font-mono)', margin: 0, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}
                title={status.relayAddress || 'None'}
              >
                {status.relayAddress || 'None'}
              </p>
            </div>
            <div style={{ background: 'rgba(255,255,255,0.02)', padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--panel-border)', minWidth: 0 }}>
              <p style={{ fontSize: '10px', color: 'var(--text-secondary)', textTransform: 'uppercase', marginBottom: '4px', margin: 0 }}>Local IP Address</p>
              <p 
                style={{ fontSize: '11px', fontWeight: '700', color: 'var(--accent-color)', fontFamily: 'var(--font-mono)', margin: 0, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}
                title={status.localIp || 'None'}
              >
                {status.localIp || 'None'}
              </p>
            </div>
            <div style={{ background: 'rgba(255,255,255,0.02)', padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--panel-border)', minWidth: 0 }}>
              <p style={{ fontSize: '10px', color: 'var(--text-secondary)', textTransform: 'uppercase', marginBottom: '4px', margin: 0 }}>Custom IP Subdomain</p>
              <p 
                style={{ fontSize: '11px', fontWeight: '700', color: 'var(--accent-color)', fontFamily: 'var(--font-mono)', margin: 0, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}
                title={status.subdomain ? `${status.subdomain}.pocketcraft.online` : 'None'}
              >
                {status.subdomain ? `${status.subdomain}.pocketcraft.online` : 'None'}
              </p>
            </div>
          </div>

          {resolvedServerState === 'starting' && (
            <div style={{ marginBottom: '20px', background: 'rgba(245, 158, 11, 0.08)', padding: '12px 16px', borderRadius: '12px', border: '1px solid rgba(245, 158, 11, 0.24)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                <span style={{ fontSize: '12px', fontWeight: '700', color: 'var(--warning-color)', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                  Booting Sequence
                </span>
                <span style={{ width: '12px', height: '12px', border: '2px solid var(--warning-color)', borderTopColor: 'transparent', borderRadius: '50%', display: 'inline-block', animation: 'spin 1s linear infinite' }}></span>
              </div>
              <p style={{ fontSize: '13px', color: 'var(--text-primary)', fontFamily: 'var(--font-mono)', margin: '0 0 12px 0', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {visibleBootProgress}
              </p>
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px', fontSize: '11px', color: 'var(--text-secondary)' }}>
                  <span>Startup Progress</span>
                  <span style={{ fontWeight: '700' }}>{bootProgressPercent}%</span>
                </div>
                <div style={{ width: '100%', height: '6px', background: 'rgba(255,255,255,0.08)', borderRadius: '999px', overflow: 'hidden' }}>
                  <div style={{ width: `${bootProgressPercent}%`, height: '100%', background: 'linear-gradient(90deg, var(--warning-color), #fbbf24)', borderRadius: '999px', transition: 'width 0.25s ease' }}></div>
                </div>
              </div>
            </div>
          )}

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
                disabled={!phoneOnline || resolvedServerState === 'starting' || pendingActions['start_server_{}']}
              >
                <PowerIcon /> {resolvedServerState === 'starting' || pendingActions['start_server_{}'] ? 'Starting...' : 'Start Server'}
              </button>
            )}
          </div>
        </div>

        {/* Players Registry Card */}
        <div className="panel-card" style={{ gridColumn: 'span 4' }}>
          <h2 className="card-title"><UsersIcon /> Players Registry ({allJoinedPlayers.length})</h2>
          <div className="player-list" style={{ maxHeight: '280px', overflowY: 'auto' }}>
            {allJoinedPlayers.length === 0 ? (
              <div style={{ color: 'var(--text-muted)', fontSize: '13px', textAlign: 'center', padding: '20px' }}>
                No players registered.
              </div>
            ) : (
              allJoinedPlayers.map(p => (
                <div 
                  key={p.uuid || p.name} 
                  className="player-item" 
                  style={{ cursor: 'pointer', padding: '10px 14px' }}
                  onClick={() => setProfilePlayer(p)}
                >
                  <div className="player-name-wrapper">
                    <span 
                      style={{ 
                        display: 'inline-block', 
                        width: '8px', 
                        height: '8px', 
                        borderRadius: '50%', 
                        background: p.online ? 'var(--success-color)' : '#4b5563',
                        boxShadow: p.online ? '0 0 8px var(--success-color)' : 'none'
                      }}
                    />
                    <span className="player-name">{p.name}</span>
                    <span className={`player-badge ${p.name.startsWith('.') ? 'badge-bedrock' : 'badge-java'}`}>
                      {p.name.startsWith('.') ? 'Bedrock' : 'Java'}
                    </span>
                  </div>
                  <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                    {p.online && p.pingText && (
                      <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{p.pingText}</span>
                    )}
                    <button 
                      className="btn btn-secondary" 
                      style={{ padding: '4px 10px', fontSize: '12px' }}
                      onClick={(e) => {
                        e.stopPropagation();
                        setProfilePlayer(p);
                      }}
                    >
                      Details
                    </button>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Console Command Card */}
        <ConsoleCardComponent 
          phoneOnline={phoneOnline} 
          serverRunning={status.serverRunning}
          onDispatch={dispatchCommand}
        />

        {/* AFK Helper Card */}
        <div className="panel-card" style={{ gridColumn: 'span 5' }}>
          <h2 className="card-title"><BotIcon /> AFK Helper Bots</h2>
          <p style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '15px', lineHeight: '1.5' }}>
            Spawn dummy players in-game to keep chunks loaded and farm operations active.
          </p>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'rgba(255,255,255,0.03)', padding: '12px 16px', borderRadius: '12px', border: '1px solid rgba(255,255,255,0.04)', marginBottom: '16px' }}>
            <div>
              <p style={{ fontWeight: '700', fontSize: '14px', margin: 0 }}>All AFK Bots</p>
              <p style={{ fontSize: '11px', color: 'var(--text-muted)', margin: 0 }}>Status on active world</p>
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

          {/* Bot list */}
          <div style={{ marginBottom: '20px' }}>
            <p style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: '700', marginBottom: '8px', margin: 0 }}>Bot List ({status.afkBots?.length || 0})</p>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '180px', overflowY: 'auto', paddingRight: '4px' }}>
              {(!status.afkBots || status.afkBots.length === 0) ? (
                <div style={{ padding: '12px', background: 'rgba(255,255,255,0.01)', border: '1px dashed var(--panel-border)', borderRadius: '8px', textAlign: 'center' }}>
                  <p style={{ fontSize: '12px', color: 'var(--text-muted)', margin: 0 }}>No AFK bots configured.</p>
                </div>
              ) : (
                status.afkBots.map((bot) => (
                  <div key={bot.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'rgba(255,255,255,0.02)', padding: '8px 12px', borderRadius: '8px', border: '1px solid var(--panel-border)' }}>
                    <div>
                      <p style={{ fontSize: '13px', fontWeight: '700', margin: 0 }}>{bot.name}</p>
                      <p style={{ fontSize: '10px', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)', margin: 0 }}>
                        📍 {bot.x}, {bot.y}, {bot.z}
                      </p>
                    </div>
                    <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                      <button
                        className={`btn ${bot.active ? 'btn-primary' : 'btn-secondary'}`}
                        style={{ padding: '4px 8px', fontSize: '11px' }}
                        onClick={() => handleAction('toggle_afk_bot_individual', { id: bot.id }, `Toggled active state for bot ${bot.name}`)}
                        disabled={!phoneOnline || pendingActions[`toggle_afk_bot_individual_${JSON.stringify({ id: bot.id })}`]}
                      >
                        {bot.active ? 'Active' : 'Inactive'}
                      </button>
                      <button
                        className="btn btn-danger"
                        style={{ padding: '4px 8px', fontSize: '11px' }}
                        onClick={() => handleAction('delete_afk_bot', { id: bot.id }, `Deleted bot ${bot.name}`)}
                        disabled={!phoneOnline || pendingActions[`delete_afk_bot_${JSON.stringify({ id: bot.id })}`]}
                      >
                        Delete
                      </button>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>

          {/* Add Bot Form */}
          <div style={{ borderTop: '1px solid var(--panel-border)', paddingTop: '16px' }}>
            <p style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: '700', marginBottom: '8px', margin: 0 }}>Create AFK Bot</p>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <input
                type="text"
                className="form-control"
                placeholder="Bot Name (e.g. Steve)"
                value={newBotName}
                onChange={(e) => setNewBotName(e.target.value.replace(/[^a-zA-Z0-9_]/g, ''))}
                style={{ padding: '6px 12px', fontSize: '12px', background: 'rgba(0,0,0,0.3)', border: '1px solid var(--panel-border)', borderRadius: '6px', color: 'var(--text-primary)' }}
                disabled={!phoneOnline}
              />
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '6px' }}>
                <input
                  type="number"
                  className="form-control"
                  placeholder="X"
                  value={newBotX}
                  onChange={(e) => setNewBotX(e.target.value)}
                  style={{ padding: '6px 12px', fontSize: '12px', background: 'rgba(0,0,0,0.3)', border: '1px solid var(--panel-border)', borderRadius: '6px', color: 'var(--text-primary)' }}
                  disabled={!phoneOnline}
                />
                <input
                  type="number"
                  className="form-control"
                  placeholder="Y"
                  value={newBotY}
                  onChange={(e) => setNewBotY(e.target.value)}
                  style={{ padding: '6px 12px', fontSize: '12px', background: 'rgba(0,0,0,0.3)', border: '1px solid var(--panel-border)', borderRadius: '6px', color: 'var(--text-primary)' }}
                  disabled={!phoneOnline}
                />
                <input
                  type="number"
                  className="form-control"
                  placeholder="Z"
                  value={newBotZ}
                  onChange={(e) => setNewBotZ(e.target.value)}
                  style={{ padding: '6px 12px', fontSize: '12px', background: 'rgba(0,0,0,0.3)', border: '1px solid var(--panel-border)', borderRadius: '6px', color: 'var(--text-primary)' }}
                  disabled={!phoneOnline}
                />
              </div>
              <button
                className="btn btn-primary"
                style={{ padding: '8px 16px', fontSize: '12px', marginTop: '4px' }}
                onClick={() => {
                  if (newBotName.trim()) {
                    handleAction('create_afk_bot', {
                      name: newBotName.trim(),
                      x: Number(newBotX) || 0,
                      y: Number(newBotY) || 64,
                      z: Number(newBotZ) || 0
                    }, `Added bot ${newBotName.trim()}`);
                    setNewBotName('');
                    setNewBotX('');
                    setNewBotY('');
                    setNewBotZ('');
                  }
                }}
                disabled={!phoneOnline || !newBotName.trim() || pendingActions[`create_afk_bot_${JSON.stringify({ name: newBotName.trim(), x: Number(newBotX) || 0, y: Number(newBotY) || 64, z: Number(newBotZ) || 0 })}`]}
              >
                Add Bot
              </button>
            </div>
          </div>
        </div>

        {/* Whitelist Card */}
        <WhitelistCardComponent 
          whitelist={status.whitelist} 
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

        {/* Server Properties Card */}
        <div className="panel-card" style={{ gridColumn: 'span 7' }}>
          <h2 className="card-title">
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={{ verticalAlign: 'middle' }}>
              <path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.1a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/>
              <circle cx="12" cy="12" r="3"/>
            </svg>
            Server Properties
          </h2>
          
          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            {/* Difficulty */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <p style={{ fontWeight: '700', fontSize: '14px', margin: 0 }}>Difficulty</p>
                <p style={{ fontSize: '11px', color: 'var(--text-muted)', margin: 0 }}>Set game difficulty level</p>
              </div>
              <select 
                className="form-control"
                style={{ padding: '8px 12px', background: 'rgba(0,0,0,0.3)', color: 'var(--text-primary)', border: '1px solid var(--panel-border)', borderRadius: '8px' }}
                value={status.properties?.difficulty || 'normal'}
                onChange={(e) => handleAction('update_property', { key: 'difficulty', value: e.target.value }, `Difficulty set to ${e.target.value}`)}
                disabled={!phoneOnline}
              >
                <option value="peaceful">Peaceful</option>
                <option value="easy">Easy</option>
                <option value="normal">Normal</option>
                <option value="hard">Hard</option>
              </select>
            </div>

            {/* Gamemode */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <p style={{ fontWeight: '700', fontSize: '14px', margin: 0 }}>Game Mode</p>
                <p style={{ fontSize: '11px', color: 'var(--text-muted)', margin: 0 }}>Default player game mode</p>
              </div>
              <select 
                className="form-control"
                style={{ padding: '8px 12px', background: 'rgba(0,0,0,0.3)', color: 'var(--text-primary)', border: '1px solid var(--panel-border)', borderRadius: '8px' }}
                value={status.properties?.gamemode || 'survival'}
                onChange={(e) => handleAction('update_property', { key: 'gamemode', value: e.target.value }, `Game Mode set to ${e.target.value}`)}
                disabled={!phoneOnline}
              >
                <option value="survival">Survival</option>
                <option value="creative">Creative</option>
                <option value="adventure">Adventure</option>
                <option value="spectator">Spectator</option>
              </select>
            </div>

            {/* PVP Toggle */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <p style={{ fontWeight: '700', fontSize: '14px', margin: 0 }}>PVP (Player vs Player)</p>
                <p style={{ fontSize: '11px', color: 'var(--text-muted)', margin: 0 }}>Allow players to damage each other</p>
              </div>
              <label className="switch">
                <input 
                  type="checkbox"
                  checked={status.properties?.pvp !== 'false'}
                  onChange={(e) => handleAction('update_property', { key: 'pvp', value: e.target.checked ? 'true' : 'false' }, `PVP set to ${e.target.checked}`)}
                  disabled={!phoneOnline}
                />
                <span className="slider"></span>
              </label>
            </div>

            {/* Whitelist Toggle */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <p style={{ fontWeight: '700', fontSize: '14px', margin: 0 }}>Whitelist</p>
                <p style={{ fontSize: '11px', color: 'var(--text-muted)', margin: 0 }}>Only allow whitelisted players to join</p>
              </div>
              <label className="switch">
                <input 
                  type="checkbox"
                  checked={status.properties?.whiteList === 'true'}
                  onChange={(e) => handleAction('update_property', { key: 'white-list', value: e.target.checked ? 'true' : 'false' }, `Whitelist set to ${e.target.checked}`)}
                  disabled={!phoneOnline}
                />
                <span className="slider"></span>
              </label>
            </div>

            {/* Max Players */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <p style={{ fontWeight: '700', fontSize: '14px', margin: 0 }}>Max Players: <span style={{ color: 'var(--accent-color)', fontWeight: '800' }}>{maxPlayersVal}</span></p>
                  <p style={{ fontSize: '11px', color: 'var(--text-muted)', margin: 0 }}>Maximum concurrent players (1-50)</p>
                </div>
              </div>
              <input 
                type="range"
                min="1"
                max="50"
                style={{ width: '100%', accentColor: 'var(--accent-color)', cursor: 'pointer', height: '6px', background: 'rgba(255,255,255,0.1)', borderRadius: '3px' }}
                value={maxPlayersVal}
                onChange={(e) => setSliderMaxPlayers(e.target.value)}
                onMouseUp={(e) => handleAction('update_property', { key: 'max-players', value: e.currentTarget.value }, `Max players set to ${e.currentTarget.value}`)}
                onTouchEnd={(e) => handleAction('update_property', { key: 'max-players', value: e.currentTarget.value }, `Max players set to ${e.currentTarget.value}`)}
                disabled={!phoneOnline}
              />
            </div>

            {/* Spawn Protection */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <p style={{ fontWeight: '700', fontSize: '14px', margin: 0 }}>Spawn Protection: <span style={{ color: 'var(--accent-color)', fontWeight: '800' }}>{spawnProtectionVal} blocks</span></p>
                  <p style={{ fontSize: '11px', color: 'var(--text-muted)', margin: 0 }}>Spawn area protection radius in blocks (0-128)</p>
                </div>
              </div>
              <input 
                type="range"
                min="0"
                max="128"
                style={{ width: '100%', accentColor: 'var(--accent-color)', cursor: 'pointer', height: '6px', background: 'rgba(255,255,255,0.1)', borderRadius: '3px' }}
                value={spawnProtectionVal}
                onChange={(e) => setSliderSpawnProtection(e.target.value)}
                onMouseUp={(e) => handleAction('update_property', { key: 'spawn-protection', value: e.currentTarget.value }, `Spawn protection set to ${e.currentTarget.value}`)}
                onTouchEnd={(e) => handleAction('update_property', { key: 'spawn-protection', value: e.currentTarget.value }, `Spawn protection set to ${e.currentTarget.value}`)}
                disabled={!phoneOnline}
              />
            </div>

            {/* View Distance */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <p style={{ fontWeight: '700', fontSize: '14px', margin: 0 }}>View Distance: <span style={{ color: 'var(--accent-color)', fontWeight: '800' }}>{viewDistanceVal} chunks</span></p>
                  <p style={{ fontSize: '11px', color: 'var(--text-muted)', margin: 0 }}>Render distance in chunks (3-32)</p>
                </div>
              </div>
              <input 
                type="range"
                min="3"
                max="32"
                style={{ width: '100%', accentColor: 'var(--accent-color)', cursor: 'pointer', height: '6px', background: 'rgba(255,255,255,0.1)', borderRadius: '3px' }}
                value={viewDistanceVal}
                onChange={(e) => setSliderViewDistance(e.target.value)}
                onMouseUp={(e) => handleAction('update_property', { key: 'view-distance', value: e.currentTarget.value }, `View distance set to ${e.currentTarget.value}`)}
                onTouchEnd={(e) => handleAction('update_property', { key: 'view-distance', value: e.currentTarget.value }, `View distance set to ${e.currentTarget.value}`)}
                disabled={!phoneOnline}
              />
            </div>

            {/* Simulation Distance */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <p style={{ fontWeight: '700', fontSize: '14px', margin: 0 }}>Simulation Distance: <span style={{ color: 'var(--accent-color)', fontWeight: '800' }}>{simulationDistanceVal} chunks</span></p>
                  <p style={{ fontSize: '11px', color: 'var(--text-muted)', margin: 0 }}>Simulation radius in chunks (3-32)</p>
                </div>
              </div>
              <input 
                type="range"
                min="3"
                max="32"
                style={{ width: '100%', accentColor: 'var(--accent-color)', cursor: 'pointer', height: '6px', background: 'rgba(255,255,255,0.1)', borderRadius: '3px' }}
                value={simulationDistanceVal}
                onChange={(e) => setSliderSimulationDistance(e.target.value)}
                onMouseUp={(e) => handleAction('update_property', { key: 'simulation-distance', value: e.currentTarget.value }, `Simulation distance set to ${e.currentTarget.value}`)}
                onTouchEnd={(e) => handleAction('update_property', { key: 'simulation-distance', value: e.currentTarget.value }, `Simulation distance set to ${e.currentTarget.value}`)}
                disabled={!phoneOnline}
              />
            </div>

            {/* Divider / Subheader */}
            <div style={{ borderTop: '1px solid var(--panel-border)', paddingTop: '16px', marginTop: '8px' }}>
              <h3 style={{ fontSize: '13px', fontWeight: '800', color: 'var(--accent-color)', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '2px' }}>World & Generator Settings</h3>
            </div>

            {/* Server Display Name */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <p style={{ fontWeight: '700', fontSize: '14px', margin: 0 }}>Server Display Name</p>
                <p style={{ fontSize: '11px', color: 'var(--text-muted)', margin: 0 }}>Custom display name for the server</p>
              </div>
              <input 
                type="text"
                className="form-control"
                style={{ width: '180px', padding: '6px 8px', background: 'rgba(0,0,0,0.3)', color: 'var(--text-primary)', border: '1px solid var(--panel-border)', borderRadius: '8px' }}
                defaultValue={status.properties?.worldDisplayName || status.currentWorld || 'world'}
                onBlur={(e) => {
                  const val = e.target.value.trim();
                  const key = `pocketcraft-world-display.${status.currentWorld || 'world'}`;
                  if (val) {
                    handleAction('update_property', { key, value: val }, `Server display name set to ${val}`);
                  }
                }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.currentTarget.blur();
                  }
                }}
                disabled={!phoneOnline}
              />
            </div>

            {/* Server Description */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <p style={{ fontWeight: '700', fontSize: '14px', margin: 0 }}>Server Description</p>
                <p style={{ fontSize: '11px', color: 'var(--text-muted)', margin: 0 }}>Description shown in multiplayer server list</p>
              </div>
              <input 
                type="text"
                className="form-control"
                style={{ width: '180px', padding: '6px 8px', background: 'rgba(0,0,0,0.3)', color: 'var(--text-primary)', border: '1px solid var(--panel-border)', borderRadius: '8px' }}
                defaultValue={status.properties?.worldDescription || 'Hosted on Pocketcraft'}
                onBlur={(e) => {
                  const val = e.target.value.trim();
                  const key = `pocketcraft-world-description.${status.currentWorld || 'world'}`;
                  if (val) {
                    handleAction('update_property', { key, value: val }, `Server description set to ${val}`);
                  }
                }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.currentTarget.blur();
                  }
                }}
                disabled={!phoneOnline}
              />
            </div>

            {/* World Seed */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <p style={{ fontWeight: '700', fontSize: '14px', margin: 0 }}>World Seed</p>
                <p style={{ fontSize: '11px', color: 'var(--text-muted)', margin: 0 }}>Seed for generating new chunks</p>
              </div>
              <input 
                type="text"
                className="form-control"
                style={{ width: '180px', padding: '6px 8px', background: 'rgba(0,0,0,0.3)', color: 'var(--text-primary)', border: '1px solid var(--panel-border)', borderRadius: '8px' }}
                defaultValue={status.properties?.levelSeed || ''}
                onBlur={(e) => {
                  const val = e.target.value.trim();
                  if (val !== status.properties?.levelSeed) {
                    handleAction('update_property', { key: 'level-seed', value: val }, `World seed set to ${val}`);
                  }
                }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.currentTarget.blur();
                  }
                }}
                disabled={!phoneOnline}
              />
            </div>

            {/* Allow Nether */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <p style={{ fontWeight: '700', fontSize: '14px', margin: 0 }}>Allow Nether</p>
                <p style={{ fontSize: '11px', color: 'var(--text-muted)', margin: 0 }}>Enable nether dimension generation</p>
              </div>
              <label className="switch">
                <input 
                  type="checkbox"
                  checked={status.properties?.allowNether !== 'false'}
                  onChange={(e) => handleAction('update_property', { key: 'allow-nether', value: e.target.checked ? 'true' : 'false' }, `Allow Nether set to ${e.target.checked}`)}
                  disabled={!phoneOnline}
                />
                <span className="slider"></span>
              </label>
            </div>

            {/* Hardcore Mode */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <p style={{ fontWeight: '700', fontSize: '14px', margin: 0 }}>Hardcore Mode</p>
                <p style={{ fontSize: '11px', color: 'var(--text-muted)', margin: 0 }}>Enforce hardcore rules (permanent death)</p>
              </div>
              <label className="switch">
                <input 
                  type="checkbox"
                  checked={status.properties?.hardcore === 'true'}
                  onChange={(e) => handleAction('update_property', { key: 'hardcore', value: e.target.checked ? 'true' : 'false' }, `Hardcore set to ${e.target.checked}`)}
                  disabled={!phoneOnline}
                />
                <span className="slider"></span>
              </label>
            </div>

            {/* Spawn Monsters */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <p style={{ fontWeight: '700', fontSize: '14px', margin: 0 }}>Spawn Monsters</p>
                <p style={{ fontSize: '11px', color: 'var(--text-muted)', margin: 0 }}>Spawn hostile monsters automatically</p>
              </div>
              <label className="switch">
                <input 
                  type="checkbox"
                  checked={status.properties?.spawnMonsters !== 'false'}
                  onChange={(e) => handleAction('update_property', { key: 'spawn-monsters', value: e.target.checked ? 'true' : 'false' }, `Spawn monsters set to ${e.target.checked}`)}
                  disabled={!phoneOnline}
                />
                <span className="slider"></span>
              </label>
            </div>

            {/* Generate Structures */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <p style={{ fontWeight: '700', fontSize: '14px', margin: 0 }}>Generate Structures</p>
                <p style={{ fontSize: '11px', color: 'var(--text-muted)', margin: 0 }}>Generate villages, dungeons, temples</p>
              </div>
              <label className="switch">
                <input 
                  type="checkbox"
                  checked={status.properties?.generateStructures !== 'false'}
                  onChange={(e) => handleAction('update_property', { key: 'generate-structures', value: e.target.checked ? 'true' : 'false' }, `Generate structures set to ${e.target.checked}`)}
                  disabled={!phoneOnline}
                />
                <span className="slider"></span>
              </label>
            </div>
          </div>
        </div>

        {/* World Manager Card */}
        <div className="panel-card" style={{ gridColumn: 'span 5' }}>
          <h2 className="card-title">
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={{ verticalAlign: 'middle' }}>
              <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"/>
            </svg>
            World Manager
          </h2>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <div>
              <p style={{ fontWeight: '700', fontSize: '14px', marginBottom: '8px', margin: 0 }}>Active World</p>
              <div style={{ background: 'rgba(168, 85, 247, 0.1)', border: '1px solid rgba(168, 85, 247, 0.3)', borderRadius: '12px', padding: '10px 14px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontWeight: '800', color: 'var(--accent-color)' }}>{status.currentWorld || 'world'}</span>
                <span style={{ fontSize: '10px', textTransform: 'uppercase', fontWeight: '700', color: 'var(--accent-color)', background: 'rgba(168, 85, 247, 0.15)', padding: '2px 6px', borderRadius: '4px' }}>Active</span>
              </div>
            </div>

            <div>
              <p style={{ fontWeight: '700', fontSize: '14px', marginBottom: '8px', margin: 0 }}>Available Worlds</p>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '140px', overflowY: 'auto' }}>
                {(status.worlds || ['world']).map(world => (
                  <div key={world} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.05)', borderRadius: '8px', padding: '8px 12px' }}>
                    <span style={{ fontSize: '13px' }}>{world}</span>
                    {world !== status.currentWorld && (
                      <button
                        className="btn btn-secondary"
                        style={{ padding: '4px 8px', fontSize: '12px' }}
                        onClick={() => handleAction('switch_world', { worldName: world }, `Active world switched to ${world}`)}
                        disabled={!phoneOnline || status.serverRunning}
                      >
                        Switch
                      </button>
                    )}
                  </div>
                ))}
              </div>
              {status.serverRunning && (
                <p style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '4px', margin: 0 }}>Stop the server to switch worlds</p>
              )}
            </div>

          </div>
        </div>
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

      {profilePlayer && (
        <div className="modal-overlay" onClick={() => setProfilePlayer(null)}>
          <div 
            className="modal-content" 
            style={{ maxWidth: '760px', width: '95%', padding: '28px', textAlign: 'left', display: 'flex', flexDirection: 'row', gap: '24px', flexWrap: 'wrap' }}
            onClick={(e) => e.stopPropagation()}
          >
            {/* Left Column - Avatar & Platform */}
            <div style={{ flex: '1 1 200px', display: 'flex', flexDirection: 'column', alignItems: 'center', background: 'rgba(0,0,0,0.15)', borderRadius: '16px', padding: '20px', border: '1px solid var(--panel-border)', minWidth: '180px' }}>
              <img 
                src={profilePlayer.name.startsWith('.') 
                  ? "https://minotar.net/helm/Steve/120.png" 
                  : `https://crafatar.com/renders/body/${profilePlayer.uuid || profilePlayer.name}?size=120&overlay`}
                onError={(e) => {
                  (e.target as HTMLImageElement).src = `https://minotar.net/armor/body/${profilePlayer.name}/120.png`;
                }}
                alt="Avatar"
                style={{ width: '120px', height: '160px', objectFit: 'contain', marginBottom: '16px' }}
              />
              <h3 style={{ fontSize: '20px', fontWeight: '800', marginBottom: '8px', textAlign: 'center' }}>{profilePlayer.name}</h3>
              <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', justifyContent: 'center' }}>
                <span className={`player-badge ${profilePlayer.name.startsWith('.') ? 'badge-bedrock' : 'badge-java'}`}>
                  {profilePlayer.name.startsWith('.') ? 'Bedrock' : 'Java'}
                </span>
                {profilePlayer.isOp && (
                  <span className="player-badge" style={{ background: 'rgba(239, 68, 68, 0.15)', color: '#ef4444' }}>OP</span>
                )}
                {profilePlayer.isWhitelisted && (
                  <span className="player-badge" style={{ background: 'rgba(34, 197, 94, 0.15)', color: '#22c55e' }}>Whitelisted</span>
                )}
              </div>
            </div>

            {/* Right Column - Server Details & Actions */}
            <div style={{ flex: '2 1 400px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div>
                <h4 style={{ fontSize: '14px', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '8px' }}>Player Status & Metrics</h4>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: '10px' }}>
                  
                  <div style={{ background: 'rgba(255,255,255,0.02)', padding: '10px', borderRadius: '10px', border: '1px solid var(--panel-border)' }}>
                    <p style={{ fontSize: '10px', color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '4px', margin: 0 }}>Coordinates</p>
                    <p style={{ fontSize: '13px', fontWeight: '700', fontFamily: 'var(--font-mono)', margin: 0 }}>
                      {profilePlayer.x !== undefined ? `${profilePlayer.x.toFixed(0)}, ${profilePlayer.y.toFixed(0)}, ${profilePlayer.z.toFixed(0)}` : 'N/A'}
                    </p>
                  </div>

                  <div style={{ background: 'rgba(255,255,255,0.02)', padding: '10px', borderRadius: '10px', border: '1px solid var(--panel-border)' }}>
                    <p style={{ fontSize: '10px', color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '4px', margin: 0 }}>Dimension</p>
                    <p style={{ fontSize: '13px', fontWeight: '700', margin: 0 }}>
                      {profilePlayer.dimension || 'Overworld'}
                    </p>
                  </div>

                  <div style={{ background: 'rgba(255,255,255,0.02)', padding: '10px', borderRadius: '10px', border: '1px solid var(--panel-border)' }}>
                    <p style={{ fontSize: '10px', color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '4px', margin: 0 }}>Health</p>
                    <p style={{ fontSize: '13px', fontWeight: '700', color: 'var(--danger-color)', margin: 0 }}>
                      ❤️ {profilePlayer.health !== undefined ? `${(profilePlayer.health).toFixed(0)}/20` : '20/20'}
                    </p>
                  </div>

                  <div style={{ background: 'rgba(255,255,255,0.02)', padding: '10px', borderRadius: '10px', border: '1px solid var(--panel-border)' }}>
                    <p style={{ fontSize: '10px', color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '4px', margin: 0 }}>XP Level</p>
                    <p style={{ fontSize: '13px', fontWeight: '700', margin: 0 }}>
                      ✨ {profilePlayer.xpLevel !== undefined ? profilePlayer.xpLevel : '0'}
                    </p>
                  </div>

                  <div style={{ background: 'rgba(255,255,255,0.02)', padding: '10px', borderRadius: '10px', border: '1px solid var(--panel-border)' }}>
                    <p style={{ fontSize: '10px', color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '4px', margin: 0 }}>Ping</p>
                    <p style={{ fontSize: '13px', fontWeight: '700', color: 'var(--success-color)', margin: 0 }}>
                      📶 {profilePlayer.pingText || (profilePlayer.online ? 'Online' : 'Offline')}
                    </p>
                  </div>

                  <div style={{ background: 'rgba(255,255,255,0.02)', padding: '10px', borderRadius: '10px', border: '1px solid var(--panel-border)' }}>
                    <p style={{ fontSize: '10px', color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '4px', margin: 0 }}>Game Mode</p>
                    <p style={{ fontSize: '13px', fontWeight: '700', margin: 0 }}>
                      {profilePlayer.gamemode || 'Survival'}
                    </p>
                  </div>

                  {/* Playtime */}
                  <div style={{ background: 'rgba(255,255,255,0.02)', padding: '10px', borderRadius: '10px', border: '1px solid var(--panel-border)' }}>
                    <p style={{ fontSize: '10px', color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '4px', margin: 0 }}>Playtime</p>
                    <p style={{ fontSize: '13px', fontWeight: '700', margin: 0 }}>
                      ⏱️ {profilePlayer.playtime !== undefined ? (() => {
                        const sec = parseInt(profilePlayer.playtime);
                        if (!sec) return '0m';
                        const h = Math.floor(sec / 3600);
                        const m = Math.floor((sec % 3600) / 60);
                        return h > 0 ? `${h}h ${m}m` : `${m}m`;
                      })() : 'N/A'}
                    </p>
                  </div>

                  {/* Hunger */}
                  <div style={{ background: 'rgba(255,255,255,0.02)', padding: '10px', borderRadius: '10px', border: '1px solid var(--panel-border)' }}>
                    <p style={{ fontSize: '10px', color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '4px', margin: 0 }}>Hunger</p>
                    <p style={{ fontSize: '13px', fontWeight: '700', color: '#f97316', margin: 0 }}>
                      🍖 {profilePlayer.hunger !== undefined ? `${profilePlayer.hunger}/20` : '20/20'}
                    </p>
                  </div>

                  {/* Deaths */}
                  <div style={{ background: 'rgba(255,255,255,0.02)', padding: '10px', borderRadius: '10px', border: '1px solid var(--panel-border)' }}>
                    <p style={{ fontSize: '10px', color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '4px', margin: 0 }}>Deaths</p>
                    <p style={{ fontSize: '13px', fontWeight: '700', color: '#ef4444', margin: 0 }}>
                      💀 {profilePlayer.deaths !== undefined ? profilePlayer.deaths : '0'}
                    </p>
                  </div>

                  {/* Last Death */}
                  <div style={{ background: 'rgba(255,255,255,0.02)', padding: '10px', borderRadius: '10px', border: '1px solid var(--panel-border)', gridColumn: 'span 2', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                      <p style={{ fontSize: '10px', color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '4px', margin: 0 }}>Last Death Position</p>
                      <p style={{ fontSize: '12px', fontWeight: '700', fontFamily: 'var(--font-mono)', margin: 0 }}>
                        📍 {profilePlayer.deathX !== undefined && profilePlayer.deathX !== 0 
                          ? `${profilePlayer.deathX.toFixed(0)}, ${profilePlayer.deathY.toFixed(0)}, ${profilePlayer.deathZ.toFixed(0)} (${profilePlayer.deathDim ? profilePlayer.deathDim.replace('minecraft:', '') : 'overworld'})` 
                          : 'None'}
                      </p>
                    </div>
                    {profilePlayer.deathX !== undefined && profilePlayer.deathX !== 0 && (
                      <button
                        className="btn btn-secondary"
                        style={{ padding: '4px 8px', fontSize: '10px' }}
                        onClick={() => handleAction('rcon', { command: `tp "${profilePlayer.name}" ${profilePlayer.deathX.toFixed(0)} ${profilePlayer.deathY.toFixed(0)} ${profilePlayer.deathZ.toFixed(0)}` }, `Teleported ${profilePlayer.name} to last death location`)}
                        disabled={!phoneOnline || !status.serverRunning || !profilePlayer.online}
                      >
                        Teleport
                      </button>
                    )}
                  </div>
                </div>
              </div>
              
              <div style={{ borderTop: '1px solid var(--panel-border)', paddingTop: '12px' }}>
                <p style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: '700', marginBottom: '8px', margin: 0 }}>Administrative Commands</p>
                <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginBottom: '8px' }}>
                  <button
                    className="btn btn-secondary"
                    style={{ padding: '6px 12px', fontSize: '12px' }}
                    onClick={() => handleAction('rcon', { command: `op "${profilePlayer.name}"` }, `Made ${profilePlayer.name} OP`)}
                    disabled={!phoneOnline || !status.serverRunning}
                  >
                    OP
                  </button>
                  <button
                    className="btn btn-secondary"
                    style={{ padding: '6px 12px', fontSize: '12px' }}
                    onClick={() => handleAction('rcon', { command: `deop "${profilePlayer.name}"` }, `Removed OP from ${profilePlayer.name}`)}
                    disabled={!phoneOnline || !status.serverRunning}
                  >
                    De-OP
                  </button>
                  <button
                    className="btn btn-secondary"
                    style={{ padding: '6px 12px', fontSize: '12px' }}
                    onClick={() => handleAction('rcon', { command: `gamemode creative "${profilePlayer.name}"` }, `Set Creative Mode`)}
                    disabled={!phoneOnline || !status.serverRunning || !profilePlayer.online}
                  >
                    Creative
                  </button>
                  <button
                    className="btn btn-secondary"
                    style={{ padding: '6px 12px', fontSize: '12px' }}
                    onClick={() => handleAction('rcon', { command: `gamemode survival "${profilePlayer.name}"` }, `Set Survival Mode`)}
                    disabled={!phoneOnline || !status.serverRunning || !profilePlayer.online}
                  >
                    Survival
                  </button>
                </div>

                <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                  <button
                    className="btn btn-secondary"
                    style={{ padding: '6px 12px', fontSize: '12px' }}
                    onClick={() => handleAction(profilePlayer.isWhitelisted ? 'whitelist_remove' : 'whitelist_add', { playerName: profilePlayer.name }, `Whitelist toggled`)}
                    disabled={!phoneOnline || !status.serverRunning}
                  >
                    {profilePlayer.isWhitelisted ? 'Remove Whitelist' : 'Add Whitelist'}
                  </button>
                  <button
                    className="btn btn-secondary"
                    style={{ padding: '6px 12px', fontSize: '12px' }}
                    onClick={() => {
                      setProfilePlayer(null);
                      triggerConfirm('Ban Player', `Ban ${profilePlayer.name}?`, () => handleAction('ban', { playerName: profilePlayer.name }, `Banned ${profilePlayer.name}`));
                    }}
                    disabled={!phoneOnline || !status.serverRunning}
                  >
                    Ban
                  </button>
                  {profilePlayer.online && (
                    <button
                      className="btn btn-danger"
                      style={{ padding: '6px 12px', fontSize: '12px' }}
                      onClick={() => {
                        setProfilePlayer(null);
                        handleAction('kick', { playerName: profilePlayer.name }, `Kicked ${profilePlayer.name}`);
                      }}
                      disabled={!phoneOnline || !status.serverRunning}
                    >
                      Kick
                    </button>
                  )}
                </div>

                {/* Additional Quick commands: Heal, Kill, Hunger */}
                <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginTop: '12px' }}>
                  <button
                    className="btn btn-secondary"
                    style={{ padding: '6px 12px', fontSize: '12px' }}
                    onClick={() => handleAction('rcon', { command: `effect give "${profilePlayer.name}" instant_health 1 255 true` }, `Healed ${profilePlayer.name}`)}
                    disabled={!phoneOnline || !status.serverRunning || !profilePlayer.online}
                  >
                    Heal
                  </button>
                  <button
                    className="btn btn-secondary"
                    style={{ padding: '6px 12px', fontSize: '12px' }}
                    onClick={() => handleAction('rcon', { command: `effect give "${profilePlayer.name}" saturation 1 255 true` }, `Filled hunger of ${profilePlayer.name}`)}
                    disabled={!phoneOnline || !status.serverRunning || !profilePlayer.online}
                  >
                    Fill Hunger
                  </button>
                  <button
                    className="btn btn-secondary"
                    style={{ padding: '6px 12px', fontSize: '12px', background: 'rgba(239, 68, 68, 0.1)', color: '#ef4444', border: '1px solid rgba(239, 68, 68, 0.2)' }}
                    onClick={() => handleAction('rcon', { command: `kill "${profilePlayer.name}"` }, `Killed ${profilePlayer.name}`)}
                    disabled={!phoneOnline || !status.serverRunning || !profilePlayer.online}
                  >
                    Kill
                  </button>
                </div>

                {/* Teleport Coordinates / Player Name */}
                <div style={{ display: 'flex', gap: '8px', alignItems: 'center', marginTop: '12px', background: 'rgba(255,255,255,0.02)', padding: '10px', borderRadius: '8px', border: '1px solid var(--panel-border)' }}>
                  <span style={{ fontSize: '12px', fontWeight: '700', color: 'var(--text-secondary)', minWidth: '60px' }}>Teleport:</span>
                  <input
                    type="text"
                    className="form-control"
                    placeholder="Coords (X Y Z) or Player Name"
                    value={tpDestination}
                    onChange={(e) => setTpDestination(e.target.value)}
                    style={{ flex: 1, padding: '4px 8px', fontSize: '12px', background: 'rgba(0,0,0,0.3)', border: '1px solid var(--panel-border)', borderRadius: '6px', color: 'var(--text-primary)' }}
                    disabled={!phoneOnline || !status.serverRunning || !profilePlayer.online}
                  />
                  <button
                    className="btn btn-primary"
                    style={{ padding: '4px 12px', fontSize: '12px' }}
                    onClick={() => {
                      if (tpDestination.trim()) {
                        handleAction('rcon', { command: `tp "${profilePlayer.name}" ${tpDestination.trim()}` }, `Teleported ${profilePlayer.name} to ${tpDestination.trim()}`);
                        setTpDestination('');
                      }
                    }}
                    disabled={!phoneOnline || !status.serverRunning || !profilePlayer.online || !tpDestination.trim()}
                  >
                    Go
                  </button>
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 'auto', borderTop: '1px solid var(--panel-border)', paddingTop: '10px' }}>
                <button className="btn btn-secondary" onClick={() => setProfilePlayer(null)}>Close</button>
              </div>
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
