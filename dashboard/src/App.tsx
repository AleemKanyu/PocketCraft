import React from 'react';

function App() {
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '100vh',
      fontFamily: 'Outfit, sans-serif',
      color: '#e2e8f0',
      background: 'radial-gradient(circle at top left, #121824, #0a0d14)',
      padding: '24px',
      textAlign: 'center'
    }}>
      <div style={{
        background: 'rgba(255, 255, 255, 0.03)',
        border: '1px solid rgba(255, 255, 255, 0.08)',
        borderRadius: '24px',
        padding: '40px 32px',
        maxWidth: '480px',
        width: '100%',
        boxShadow: '0 20px 40px rgba(0, 0, 0, 0.5)'
      }}>
        <h1 style={{
          fontSize: '32px',
          fontWeight: '800',
          marginBottom: '16px',
          background: 'linear-gradient(135deg, #10b981, #059669)',
          WebkitBackgroundClip: 'text',
          WebkitTextFillColor: 'transparent'
        }}>PocketCraft</h1>
        <h2 style={{
          fontSize: '18px',
          fontWeight: '600',
          color: '#f43f5e',
          marginBottom: '12px'
        }}>Feature Notice</h2>
        <p style={{
          fontSize: '14px',
          color: '#94a3b8',
          lineHeight: '1.6',
          margin: '0 0 24px 0'
        }}>
          The Web Dashboard feature has been removed and might be added back in the future. We apologize for any inconvenience caused.
        </p>
        <div style={{
          fontSize: '12px',
          color: '#64748b'
        }}>
          PocketCraft hosting remains fully operational on the Android app.
        </div>
      </div>
    </div>
  );
}

export default App;
